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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.UnsupportedAudioFileException

/**
 * JavaFX-free audio player that decodes and plays audio via `javax.sound.sampled` SPI pipeline.
 *
 * Supports all 5 audio formats (MP3, FLAC, OGG, AAC/M4A, WAV) via JavaSound SPI decoders.
 * Uses [SourceDataLine] for PCM output. The pump thread is a daemon with explicit drain-and-close
 * in [dispose] to prevent JVM hang.
 *
 * [stop] resets the playback position to the start; a subsequent [play] begins from the
 * beginning. Use [pause]/[resume] to preserve position across playback control changes.
 * [dispose] is idempotent (callable multiple times without error).
 *
 * @see AudioItemPlayer
 * @see SourceDataLine
 */
open class CoreAudioItemPlayer(
    private val publisher: LirpEventPublisher<AudioItemPlayerEvent.Type, AudioItemPlayerEvent> = FlowEventPublisher("CoreAudioItemPlayer")
) : LirpEventPublisher<AudioItemPlayerEvent.Type, AudioItemPlayerEvent> by publisher, AudioItemPlayer {

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
    private val pcmData = AtomicReference<ByteArray?>(null)
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

    // Byte offset in pcmData at which the currently-open SourceDataLine began playing.
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
        try {
            AudioSystem.getAudioFileFormat(file)
        } catch (e: Exception) {
            throw UnsupportedAudioPlaybackException("Cannot play audio item '${audioItem.path.fileName}': unsupported format", e)
        }
        stopCurrentPlayback()
        seekTargetMillis = -1L
        framePosition = 0L
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
        pcmData.set(null)
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
                decodeAndCachePcm(file)
                state.set(InternalState.PLAYING)
                playPcmFromCache()
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

    private fun decodeAndCachePcm(file: File) {
        val pcmStream = decodeToPcmStream(file.toPath())
        pcmStream.use { stream ->
            val buf = ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } > 0) {
                buf.write(buffer, 0, bytesRead)
            }
            val data = buf.toByteArray()
            val format = stream.format
            pcmData.set(data)
            pcmFormat.set(format)
            sourceDurationMillis =
                (data.size.toDouble() / format.frameSize / format.frameRate * 1000.0).toLong()
        }
    }

    private fun playPcmFromCache() {
        val data = pcmData.get() ?: return
        val format = pcmFormat.get() ?: return

        if (seekTargetMillis >= 0L) {
            val bytesPerMillis = (format.frameRate * format.frameSize / 1000.0).toLong()
            framePosition = seekTargetMillis * bytesPerMillis
            framePosition -= framePosition % format.frameSize.toLong()
            seekTargetMillis = -1L
        }

        var line = openLine(format)

        try {
            var offset = framePosition.toInt()
            val buffer = ByteArray(4096)
            while (offset < data.size) {
                val currentState = state.get()
                if (currentState == InternalState.STOPPED || currentState == InternalState.DISPOSED) break
                if (currentState == InternalState.PAUSED) {
                    Thread.sleep(20)
                    continue
                }
                if (seekTargetMillis >= 0L) {
                    flushAndClose()
                    val frameSz = (pcmFormat.get()?.frameSize ?: format.frameSize).toLong()
                    val bytesPerMillis = (pcmFormat.get()?.frameRate ?: format.frameRate) * frameSz / 1000.0
                    framePosition = (seekTargetMillis * bytesPerMillis).toLong().coerceIn(0, data.size.toLong())
                    framePosition -= framePosition % frameSz
                    seekTargetMillis = -1L
                    line = openLine(format)
                    offset = framePosition.toInt()
                }
                if (offset >= data.size) break
                val remaining = data.size - offset
                val chunk = minOf(buffer.size, remaining)
                System.arraycopy(data, offset, buffer, 0, chunk)
                applyVolume(buffer, chunk, format)
                framePosition += line.write(buffer, 0, chunk)
                offset = framePosition.toInt()
            }
        } finally {
            drainAndClose()
        }
    }

    private fun openLine(format: AudioFormat): SourceDataLine {
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val line = AudioSystem.getLine(info) as SourceDataLine
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
}