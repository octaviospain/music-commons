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
import net.transgressoft.commons.media.util.readAudioFileFormat
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer
import net.transgressoft.commons.music.player.AudioItemPlayer.Status
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.lirp.event.FlowEventPublisher
import net.transgressoft.lirp.event.LirpEventPublisher
import mu.KotlinLogging
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

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
    internal val publisher: LirpEventPublisher<AudioItemPlayerEvent.Type, AudioItemPlayerEvent>,
    internal val pcmStreamFactory: (Path) -> AudioInputStream,
    internal val lineFactory: (AudioFormat) -> SourceDataLine,
    internal val nanoTime: () -> Long,
    internal val stallThresholdNanos: Long
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

    internal val logger = KotlinLogging.logger {}

    internal val state = AtomicReference(InternalState.IDLE)
    internal val lineRef = AtomicReference<SourceDataLine?>(null)
    internal val currentAudioItem = AtomicReference<ReactiveAudioItem<*>?>(null)
    private val pumpThread = AtomicReference<Thread?>(null)
    private val durationProbeThread = AtomicReference<Thread?>(null)
    internal val onFinishCallback = AtomicReference<Runnable?>(null)
    internal val internalStatus = AtomicReference(Status.UNKNOWN)
    internal val pcmFormat = AtomicReference<AudioFormat?>(null)

    // Monotonically incremented for each new play() invocation. The pump thread
    // captures its session id at start; only the still-active session is permitted
    // to mutate shared playback state from its completion handler. Prevents an
    // interrupted older pump from clobbering state set up for a new playback.
    private val sessionId = AtomicLong(0L)
    internal val activeSessionId = AtomicLong(-1L)

    @Volatile internal var sourceDurationMillis: Long = 0L

    @Volatile internal var framePosition: Long = 0L

    @Volatile internal var seekPreviewMillis: Long = -1L

    internal val seekTargetMillis = AtomicLong(-1L)

    // Byte offset in the decoded stream at which the currently-open SourceDataLine began playing.
    // line.microsecondPosition resets to 0 each time a new line is opened (e.g. after pause→resume),
    // so absolute playback time = lineStartFrameOffset + line.microsecondPosition.
    @Volatile internal var lineStartFrameOffset: Long = 0L

    @Volatile internal var volume: Double = 1.0

    internal val frameSize: Int get() = pcmFormat.get()?.frameSize ?: 1
    internal val frameRate: Float get() = pcmFormat.get()?.frameRate ?: 1f

    internal val seekers: List<PcmStreamSeeker> = listOf(FlacPcmStreamSeeker, Mp3PcmStreamSeeker, OggPcmStreamSeeker)
    internal val stallDetector =
        StallDetector(stallThresholdNanos, {
            state.get() == InternalState.PLAYING
        }, { state.get() == InternalState.PAUSED }, { internalStatus.get() }, { internalStatus.set(it) })
    internal val durationProber = DurationProber(pcmStreamFactory)

    override val totalDuration: Duration get() = Duration.ofMillis(sourceDurationMillis)

    override fun status(): Status = internalStatus.get()

    override fun getCurrentTime(): Duration {
        val pendingSeekMillis = seekPreviewMillis
        if (pendingSeekMillis >= 0L && (state.get() == InternalState.PLAYING || state.get() == InternalState.PAUSED)) {
            return Duration.ofMillis(pendingSeekMillis)
        }
        val line = lineRef.get()
        val fs = frameSize.toLong()
        val rate = frameRate
        return if (line?.isOpen == true && fs > 0 && rate > 0f) {
            Duration.ofMillis(line.microsecondPosition / 1000L + framesToMillis(lineStartFrameOffset, fs, rate))
        } else if (fs > 0 && rate > 0f) {
            Duration.ofMillis(framesToMillis(framePosition, fs, rate))
        } else {
            Duration.ZERO
        }
    }

    private fun framesToMillis(byteOffset: Long, frameSize: Long, frameRate: Float): Long =
        (byteOffset * 1000.0 / frameSize / frameRate).toLong()

    @Throws(UnsupportedAudioPlaybackException::class)
    override fun play(audioItem: ReactiveAudioItem<*>) {
        if (state.get() == InternalState.DISPOSED) return
        val file = audioItem.path.toFile()
        if (!file.exists() || file.isDirectory) {
            throw UnsupportedAudioPlaybackException("Cannot play audio item '${audioItem.path.fileName}': file not found")
        }
        stopCurrentPlayback()
        seekTargetMillis.set(-1L)
        seekPreviewMillis = -1L
        framePosition = 0L
        val audioFileFormat = runCatching { readAudioFileFormat(file.toPath()) }.getOrNull()
        sourceDurationMillis =
            if (audioFileFormat != null) durationProber.durationMillis(audioFileFormat) else {
                try {
                    pcmStreamFactory(file.toPath()).use { }
                } catch (failure: Exception) {
                    throw UnsupportedAudioPlaybackException("Cannot play audio item '${audioItem.path.fileName}': unsupported format", failure)
                }
                0L
            }
        currentAudioItem.set(audioItem)
        val mySession = sessionId.incrementAndGet()
        activeSessionId.set(mySession)
        internalStatus.set(Status.PLAYING)
        pumpThread.set(PlaybackPump(this, audioItem, mySession).asThread())
        pumpThread.get()?.start()
        if (audioFileFormat == null || durationProber.requiresDecodedDurationProbe(file, audioFileFormat)) {
            val fileName = audioItem.path.fileName.toString()
            durationProbeThread.set(
                Thread({
                    val resolved =
                        runCatching { durationProber.resolvePlayableDurationMillis(file, fileName) }
                            .onFailure { logger.debug(it) { "Decoded-duration probe failed for '${file.name}' after playback started" } }
                            .getOrNull()
                    if (resolved != null && resolved > 0L && activeSessionId.get() == mySession && state.get() != InternalState.DISPOSED) {
                        sourceDurationMillis = resolved
                    }
                }, "CoreAudioPlayer-duration-probe").apply { isDaemon = true }
            )
            durationProbeThread.get()?.start()
        }
    }

    override fun pause() {
        if (state.get() != InternalState.PLAYING) return
        state.set(InternalState.PAUSED)
        lineRef.get()?.takeIf { it.isOpen }?.stop()
        internalStatus.set(Status.PAUSED)
    }

    override fun resume() {
        if (state.get() != InternalState.PAUSED) return
        state.set(InternalState.PLAYING)
        lineRef.get()?.takeIf { it.isOpen }?.start()
        internalStatus.set(Status.PLAYING)
    }

    override fun stop() {
        if (state.get() == InternalState.DISPOSED) return
        stopCurrentPlayback()
        framePosition = 0L
        lineStartFrameOffset = 0L
        seekTargetMillis.set(-1L)
        seekPreviewMillis = -1L
        internalStatus.set(Status.STOPPED)
    }

    override fun dispose() {
        if (state.get() == InternalState.DISPOSED) return
        stopCurrentPlayback()
        pcmFormat.set(null)
        sourceDurationMillis = 0L
        seekPreviewMillis = -1L
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
        val requestedMillis = position.toMillis().coerceAtLeast(0L)
        val targetMillis = if (sourceDurationMillis > 0L) requestedMillis.coerceAtMost(sourceDurationMillis) else requestedMillis
        seekTargetMillis.set(targetMillis)
        if (state.get() == InternalState.PLAYING || state.get() == InternalState.PAUSED) {
            seekPreviewMillis = targetMillis
            lineRef.get()?.flush()
        }
    }

    override fun onFinish(value: Runnable) {
        if (state.get() == InternalState.DISPOSED) return
        onFinishCallback.set(value)
    }

    private fun stopCurrentPlayback() {
        state.set(InternalState.STOPPED)
        // Invalidate the active session so a pump that is mid-startup neither flips the state
        // back to PLAYING nor rewrites it to IDLE/READY from its completion handler. play()
        // re-establishes a fresh session id immediately after calling this.
        activeSessionId.set(-1L)
        pumpThread.getAndSet(null)?.interrupt()
        durationProbeThread.getAndSet(null)?.interrupt()
        // Transport teardown must not block the caller draining buffered audio; discard it.
        flushAndClose()
    }

    internal fun drainAndClose() {
        val line = lineRef.getAndSet(null) ?: return
        try {
            line.drain()
            line.stop()
        } finally {
            line.close()
        }
    }

    internal fun flushAndClose() {
        val line = lineRef.getAndSet(null) ?: return
        try {
            line.flush()
        } finally {
            line.close()
        }
    }

    internal companion object {
        const val DEFAULT_STALL_THRESHOLD_NANOS = 100_000_000L

        fun createSourceDataLine(format: AudioFormat): SourceDataLine {
            val info = DataLine.Info(SourceDataLine::class.java, format)
            return AudioSystem.getLine(info) as SourceDataLine
        }
    }
}