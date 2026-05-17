/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.media.player

import net.transgressoft.commons.media.util.decodeToPcmStream
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer
import net.transgressoft.commons.music.player.AudioItemPlayer.Status
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Played
import net.transgressoft.lirp.event.FlowEventPublisher
import net.transgressoft.lirp.event.LirpEventPublisher
import mu.KotlinLogging
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.UnsupportedAudioFileException

/**
 * JavaFX-free audio player that decodes and plays audio via `javax.sound.sampled` SPI pipeline.
 *
 * Supports all 5 audio formats (MP3, FLAC, OGG, AAC/M4A, WAV) via JavaSound SPI decoders.
 * Uses a bounded streaming decoder pipeline with [SourceDataLine] for PCM output. The pump
 * thread is a daemon with explicit drain-and-close in [dispose] to prevent JVM hang.
 *
 * [stop] resets the playback position to the start; a subsequent [play] begins from the
 * beginning. Use [pause]/[resume] to preserve position across playback control changes.
 * [dispose] is idempotent (callable multiple times without error).
 *
 * @see AudioItemPlayer
 * @see SourceDataLine
 */
open class CoreAudioItemPlayer private constructor(
    private val publisher: LirpEventPublisher<AudioItemPlayerEvent.Type, AudioItemPlayerEvent>,
    private val pcmStreamFactory: (Path) -> AudioInputStream,
    private val lineFactory: (AudioFormat) -> SourceDataLine,
    private val nanoTime: () -> Long,
    private val stallThresholdNanos: Long
) : LirpEventPublisher<AudioItemPlayerEvent.Type, AudioItemPlayerEvent> by publisher, AudioItemPlayer {

    @JvmOverloads
    constructor(
        publisher: LirpEventPublisher<AudioItemPlayerEvent.Type, AudioItemPlayerEvent> = FlowEventPublisher("CoreAudioItemPlayer")
    ) : this(
        publisher = publisher,
        pcmStreamFactory = ::decodeToPcmStream,
        lineFactory = ::createSourceDataLine,
        nanoTime = System::nanoTime,
        stallThresholdNanos = DEFAULT_STALL_THRESHOLD_NANOS
    )

    internal constructor(
        pcmStreamFactory: (Path) -> AudioInputStream,
        lineFactory: (AudioFormat) -> SourceDataLine,
        nanoTime: () -> Long,
        stallThresholdNanos: Long
    ) : this(
        publisher = FlowEventPublisher("CoreAudioItemPlayer"),
        pcmStreamFactory = pcmStreamFactory,
        lineFactory = lineFactory,
        nanoTime = nanoTime,
        stallThresholdNanos = stallThresholdNanos
    )

    private val logger = KotlinLogging.logger {}

    private enum class InternalState {
        IDLE,
        PLAYING,
        PAUSED,
        STOPPED,
        DISPOSED
    }

    private val state = AtomicReference(InternalState.IDLE)
    private val lineRef = AtomicReference<SourceDataLine?>(null)
    private val currentAudioItem = AtomicReference<ReactiveAudioItem<*>?>(null)
    private val pumpThread = AtomicReference<Thread?>(null)
    private val onFinishCallback = AtomicReference<Runnable?>(null)
    private val internalStatus = AtomicReference(Status.UNKNOWN)
    private val pcmFormat = AtomicReference<AudioFormat?>(null)

    // Monotonically incremented for each new play() invocation. The pump thread
    // captures its session id at start; only the still-active session is permitted
    // to mutate shared playback state from its completion handler. Prevents an
    // interrupted older pump from clobbering state set up for a new playback.
    private val sessionId = AtomicLong(0L)
    private val activeSessionId = AtomicLong(-1L)

    @Volatile
    private var sourceDurationMillis: Long = 0L

    @Volatile
    private var framePosition: Long = 0L

    @Volatile
    private var seekTargetMillis: Long = -1L

    // Byte offset in the decoded stream at which the currently-open SourceDataLine began playing.
    // line.microsecondPosition resets to 0 each time a new line is opened (e.g. after pause→resume),
    // so absolute playback time = lineStartFrameOffset + line.microsecondPosition.
    @Volatile
    private var lineStartFrameOffset: Long = 0L

    @Volatile
    private var volume: Double = 1.0

    private val frameSize: Int get() = pcmFormat.get()?.frameSize ?: 1
    private val frameRate: Float get() = pcmFormat.get()?.frameRate ?: 1f

    override val totalDuration: Duration
        get() = Duration.ofMillis(sourceDurationMillis)

    override fun status(): Status = internalStatus.get()

    override fun getCurrentTime(): Duration {
        val line = lineRef.get()
        val fs = frameSize.toLong()
        val rate = frameRate
        return if (line?.isOpen == true && fs > 0 && rate > 0f) {
            val offsetMillis = framesToMillis(lineStartFrameOffset, fs, rate)
            Duration.ofMillis(line.microsecondPosition / 1000L + offsetMillis)
        } else if (fs > 0 && rate > 0f) {
            Duration.ofMillis(framesToMillis(framePosition, fs, rate))
        } else {
            Duration.ZERO
        }
    }

    private fun framesToMillis(byteOffset: Long, frameSize: Long, frameRate: Float): Long =
        (byteOffset * 1000.0 / frameSize / frameRate).toLong()

    override fun play(audioItem: ReactiveAudioItem<*>) {
        if (state.get() == InternalState.DISPOSED) return
        val file = audioItem.path.toFile()
        if (!file.exists() || file.isDirectory) {
            throw UnsupportedAudioPlaybackException("Cannot play audio item '${audioItem.path.fileName}': file not found")
        }
        val audioFileFormat =
            try {
                AudioSystem.getAudioFileFormat(file)
            } catch (e: Exception) {
                throw UnsupportedAudioPlaybackException("Cannot play audio item '${audioItem.path.fileName}': unsupported format", e)
            }
        stopCurrentPlayback()
        seekTargetMillis = -1L
        framePosition = 0L
        sourceDurationMillis = durationMillis(audioFileFormat)
        currentAudioItem.set(audioItem)
        val mySession = sessionId.incrementAndGet()
        activeSessionId.set(mySession)
        internalStatus.set(Status.PLAYING)
        pumpThread.set(createPumpThread(audioItem, mySession))
        pumpThread.get()?.start()
    }

    override fun pause() {
        if (state.get() != InternalState.PLAYING) return
        state.set(InternalState.PAUSED)
        val line = lineRef.get()
        if (line?.isOpen == true) {
            line.stop()
        }
        internalStatus.set(Status.PAUSED)
    }

    override fun resume() {
        if (state.get() != InternalState.PAUSED) return
        state.set(InternalState.PLAYING)
        val line = lineRef.get()
        if (line?.isOpen == true) line.start()
        internalStatus.set(Status.PLAYING)
    }

    override fun stop() {
        if (state.get() == InternalState.DISPOSED) return
        stopCurrentPlayback()
        framePosition = 0L
        lineStartFrameOffset = 0L
        seekTargetMillis = -1L
        internalStatus.set(Status.STOPPED)
    }

    override fun dispose() {
        if (state.get() == InternalState.DISPOSED) return
        stopCurrentPlayback()
        pcmFormat.set(null)
        sourceDurationMillis = 0L
        state.set(InternalState.DISPOSED)
        internalStatus.set(Status.DISPOSED)
        onFinishCallback.set(null)
    }

    override fun setVolume(value: Double) {
        if (state.get() == InternalState.DISPOSED) return
        volume = value.coerceIn(0.0, 1.0)
    }

    override fun seek(position: Duration) {
        if (state.get() == InternalState.DISPOSED) return
        seekTargetMillis = position.toMillis()
    }

    override fun onFinish(value: Runnable) {
        if (state.get() == InternalState.DISPOSED) return
        onFinishCallback.set(value)
    }

    private fun stopCurrentPlayback() {
        state.set(InternalState.STOPPED)
        val thread = pumpThread.getAndSet(null)
        thread?.interrupt()
        drainAndClose()
    }

    private fun drainAndClose() {
        val line = lineRef.getAndSet(null)
        if (line != null) {
            try {
                line.drain()
                line.stop()
            } finally {
                line.close()
            }
        }
    }

    private fun flushAndClose() {
        val line = lineRef.getAndSet(null)
        if (line != null) {
            try {
                line.flush()
            } finally {
                line.close()
            }
        }
    }

    private fun createPumpThread(audioItem: ReactiveAudioItem<*>, mySession: Long): Thread {
        val file = audioItem.path.toFile()
        return Thread({
            var hadError = false
            var halted = false
            try {
                state.set(InternalState.PLAYING)
                streamPcm(file)
            } catch (_: InterruptedException) {
                logger.debug { "Playback interrupted for '${audioItem.path.fileName}'" }
                hadError = true
            } catch (e: UnsupportedAudioFileException) {
                logger.error(e) { "Unsupported audio format: '$file'" }
                hadError = true
                halted = true
            } catch (e: Exception) {
                val current = state.get()
                if (current != InternalState.STOPPED && current != InternalState.DISPOSED) {
                    logger.error(e) { "Error playing '${audioItem.path.fileName}'" }
                    halted = true
                }
                hadError = true
            } finally {
                onPlaybackComplete(
                    mySession,
                    wasNaturalEnd = !hadError && state.get() == InternalState.PLAYING,
                    halted = halted
                )
            }
        }, "CoreAudioPlayer-pump").apply {
            isDaemon = true
            setUncaughtExceptionHandler { _, throwable ->
                logger.error(throwable) { "Uncaught exception in pump thread" }
            }
        }
    }

    private fun streamPcm(file: File) {
        var session = openStreamSession(file, framePosition)
        var line = openLine(session.format)
        val buffer = ByteArray(TRANSFER_BUFFER_SIZE)

        try {
            while (true) {
                val currentState = state.get()
                if (currentState == InternalState.STOPPED || currentState == InternalState.DISPOSED) break
                if (currentState == InternalState.PAUSED) {
                    Thread.sleep(PAUSE_POLL_MILLIS)
                    continue
                }

                val seekTarget = consumeSeekTarget()
                if (seekTarget >= 0L) {
                    session.stream.close()
                    flushAndClose()
                    val targetOffset = millisToByteOffset(seekTarget, session.format)
                    session = openStreamSession(file, targetOffset)
                    line = openLine(session.format)
                }

                val readStartedAt = nanoTime()
                val bytesRead = session.stream.read(buffer)
                val readDuration = nanoTime() - readStartedAt
                if (bytesRead < 0) break
                if (bytesRead == 0) continue

                updateStallStatus(readDuration)
                applyVolume(buffer, bytesRead, session.format)
                framePosition += line.write(buffer, 0, bytesRead)
            }
        } finally {
            session.stream.close()
            drainAndClose()
        }
    }

    private fun openStreamSession(file: File, requestedOffset: Long): StreamSession {
        val stream = pcmStreamFactory(file.toPath())
        val format = stream.format
        pcmFormat.set(format)
        val alignedOffset = requestedOffset.coerceAtLeast(0L).alignDown(format.frameSize.toLong())
        val skipped = skipFully(stream, alignedOffset)
        framePosition = skipped.alignDown(format.frameSize.toLong())
        return StreamSession(stream, format)
    }

    private fun consumeSeekTarget(): Long {
        val target = seekTargetMillis
        if (target >= 0L) {
            seekTargetMillis = -1L
        }
        return target
    }

    private fun skipFully(stream: AudioInputStream, requestedBytes: Long): Long {
        var remaining = requestedBytes
        var skipped = 0L
        val discard = ByteArray(TRANSFER_BUFFER_SIZE)
        while (remaining > 0L) {
            val currentState = state.get()
            if (currentState == InternalState.STOPPED || currentState == InternalState.DISPOSED) break
            val step = minOf(remaining, discard.size.toLong()).toInt()
            val bytesRead = stream.read(discard, 0, step)
            if (bytesRead < 0) break
            skipped += bytesRead
            remaining -= bytesRead
        }
        return skipped
    }

    private fun updateStallStatus(readDurationNanos: Long) {
        if (readDurationNanos >= stallThresholdNanos && state.get() == InternalState.PLAYING) {
            internalStatus.set(Status.STALLED)
        } else {
            recoverFromStallIfNeeded()
        }
    }

    private fun recoverFromStallIfNeeded() {
        if (internalStatus.get() != Status.STALLED) return
        internalStatus.set(if (state.get() == InternalState.PAUSED) Status.PAUSED else Status.PLAYING)
    }

    private fun openLine(format: AudioFormat): SourceDataLine {
        val line = lineFactory(format)
        lineRef.set(line)
        line.open(format, 4096)
        line.start()
        lineStartFrameOffset = framePosition
        return line
    }

    private fun applyVolume(buffer: ByteArray, chunk: Int, format: AudioFormat) {
        val gain = volume.toFloat()
        if (gain == 1.0f) return
        if (gain == 0.0f) {
            buffer.fill(0, 0, chunk)
            return
        }
        when (format.sampleSizeInBits) {
            16 -> {
                val byteOrder = if (format.isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
                val bb = ByteBuffer.wrap(buffer, 0, chunk).order(byteOrder)
                val sb = bb.asShortBuffer()
                for (j in 0 until sb.remaining()) {
                    val sample = sb[j]
                    val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    sb.put(j, scaled)
                }
            }
            8 -> {
                for (i in 0 until chunk) {
                    val signed = (buffer[i].toInt() and 0xFF) - 128
                    val scaled = (signed * gain).toInt().coerceIn(-128, 127)
                    buffer[i] = (scaled + 128).toByte()
                }
            }
            else ->
                logger.debug {
                    "Volume control skipped: sample size ${format.sampleSizeInBits}-bit not supported (only 8/16-bit)"
                }
        }
    }

    private fun onPlaybackComplete(mySession: Long, wasNaturalEnd: Boolean, halted: Boolean) {
        drainAndClose()
        // Stale pump (a newer play() has already started): do not mutate shared state
        // or fire callbacks that belong to the newly started playback.
        if (activeSessionId.get() != mySession) return
        val item = if (wasNaturalEnd) currentAudioItem.getAndSet(null) else null
        if (item != null) {
            publisher.emitAsync(Played(item))
            onFinishCallback.get()?.run()
        }
        if (wasNaturalEnd) {
            onFinishCallback.set(null)
        }
        if (state.get() != InternalState.DISPOSED) {
            state.set(InternalState.IDLE)
            internalStatus.set(if (halted) Status.HALTED else Status.READY)
        }
    }

    private fun durationMillis(audioFileFormat: AudioFileFormat): Long {
        val durationMicros = audioFileFormat.properties()["duration"] as? Long
        if (durationMicros != null && durationMicros > 0L) {
            return durationMicros / 1000L
        }
        val format = audioFileFormat.format
        return if (audioFileFormat.frameLength > 0 && format.frameRate > 0f) {
            (audioFileFormat.frameLength * 1000.0 / format.frameRate).toLong()
        } else {
            0L
        }
    }

    private fun millisToByteOffset(positionMillis: Long, format: AudioFormat): Long {
        val bytesPerMillis = format.frameRate * format.frameSize / 1000.0
        return (positionMillis * bytesPerMillis).toLong().alignDown(format.frameSize.toLong())
    }

    private fun Long.alignDown(step: Long): Long = if (step > 0L) this - this % step else this

    private data class StreamSession(
        val stream: AudioInputStream,
        val format: AudioFormat
    )

    private companion object {
        const val TRANSFER_BUFFER_SIZE = 4096
        const val PAUSE_POLL_MILLIS = 20L
        const val DEFAULT_STALL_THRESHOLD_NANOS = 100_000_000L

        fun createSourceDataLine(format: AudioFormat): SourceDataLine {
            val info = DataLine.Info(SourceDataLine::class.java, format)
            return AudioSystem.getLine(info) as SourceDataLine
        }
    }
}