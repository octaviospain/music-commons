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

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer.Status
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Played
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.UnsupportedAudioFileException

/**
 * Manages the lifecycle of a single playback session: opening the PCM stream, pumping
 * decoded audio to the [javax.sound.sampled.SourceDataLine], handling seek requests mid-stream,
 * and signalling completion or error back to the player. Runs entirely on the pump thread.
 *
 * Accesses player state directly through the [player] reference. Both this class and
 * [CoreAudioItemPlayer] live in the same package so that [internal] visibility suffices.
 *
 * @param player the owning player whose internal state is mutated during playback
 * @param audioItem the item being played in this session
 * @param mySession session ID assigned by the player; used to detect stale pumps
 */
internal class PlaybackPump(
    private val player: CoreAudioItemPlayer,
    private val audioItem: ReactiveAudioItem<*>,
    private val mySession: Long
) {

    private val file: File = audioItem.path.toFile()

    /**
     * Creates and returns the pump [Thread]. The thread sets the player state to PLAYING,
     * streams PCM until the stream ends or playback is interrupted, then calls [onPlaybackComplete].
     */
    fun asThread(): Thread =
        Thread({
            var hadError = false
            var halted = false
            try {
                // A stop()/dispose() racing ahead of this thread invalidates the active session;
                // bail before flipping the state to PLAYING so a stopped player is not resurrected.
                if (player.activeSessionId.get() != mySession) return@Thread
                player.state.set(InternalState.PLAYING)
                streamPcm()
            } catch (_: InterruptedException) {
                player.logger.debug { "Playback interrupted for '${audioItem.path.fileName}'" }
                hadError = true
            } catch (e: UnsupportedAudioFileException) {
                player.logger.error(e) { "Unsupported audio format: '$file'" }
                hadError = true
                halted = true
            } catch (e: Exception) {
                val current = player.state.get()
                if (current != InternalState.STOPPED && current != InternalState.DISPOSED) {
                    player.logger.error(e) { "Error playing '${audioItem.path.fileName}'" }
                    halted = true
                }
                hadError = true
            } finally {
                onPlaybackComplete(!hadError && player.state.get() == InternalState.PLAYING, halted)
            }
        }, "CoreAudioPlayer-pump").apply {
            isDaemon = true
            setUncaughtExceptionHandler { _, t -> player.logger.error(t) { "Uncaught exception in pump thread" } }
        }

    private fun streamPcm() {
        var session = openStreamSession(player.framePosition)
        var line = openLine(session.format)
        val buffer = ByteArray(TRANSFER_BUFFER_SIZE)
        var clearSeekPreviewAfterWrite = false
        try {
            while (true) {
                val currentState = player.state.get()
                if (currentState == InternalState.STOPPED || currentState == InternalState.DISPOSED) break
                if (currentState == InternalState.PAUSED) {
                    // The stall detector only runs after a successful read, so a STALLED status
                    // latched before pausing would never reconcile while paused. Reconcile it
                    // here so a paused player reports PAUSED rather than a stale STALLED.
                    player.stallDetector.recoverIfNeeded()
                    Thread.sleep(PAUSE_POLL_MILLIS)
                    continue
                }
                val seekTarget = player.seekTargetMillis.getAndSet(-1L)
                if (seekTarget >= 0L) {
                    val reopened = reopenAtSeekTarget(session, seekTarget)
                    session = reopened.session
                    line = reopened.line
                    clearSeekPreviewAfterWrite = true
                }
                val readStartedAt = player.nanoTime()
                val bytesRead = session.stream.read(buffer)
                val readDuration = player.nanoTime() - readStartedAt
                if (bytesRead < 0) break
                if (bytesRead == 0) {
                    // Some AudioInputStream implementations legally return 0 when no data is
                    // ready yet without being at EOF; back off briefly to avoid busy-spinning
                    // a CPU core.
                    Thread.sleep(ZERO_READ_BACKOFF_MILLIS)
                    continue
                }
                player.stallDetector.update(readDuration)
                PcmVolume.apply(buffer, bytesRead, session.format, player.volume.toFloat())
                player.framePosition += line.write(buffer, 0, bytesRead)
                if (clearSeekPreviewAfterWrite) {
                    player.seekPreviewMillis = -1L
                    clearSeekPreviewAfterWrite = false
                }
            }
        } finally {
            session.stream.close()
        }
    }

    /**
     * Reopens the stream and output line positioned at [seekTargetMillis]. The new session and
     * line are built into locals so a failure mid-reopen never leaves a stale/closed line
     * referenced by the player or leaks the new session stream — on `openLine` failure the new
     * session stream is closed before the exception propagates.
     */
    private fun reopenAtSeekTarget(current: StreamSession, seekTargetMillis: Long): Reopened {
        val newSession = openStreamSession(millisToByteOffset(seekTargetMillis, current.format))
        current.stream.close()
        player.flushAndClose()
        val newLine =
            try {
                openLine(newSession.format)
            } catch (e: Exception) {
                newSession.stream.close()
                throw e
            }
        return Reopened(newSession, newLine)
    }

    private fun openStreamSession(requestedOffset: Long): StreamSession {
        val seekResult = player.seekers.firstNotNullOfOrNull { it.open(file, requestedOffset) }
        if (seekResult != null) {
            val format = seekResult.stream.format
            player.pcmFormat.set(format)
            player.framePosition = seekResult.startByteOffset.alignDown(format.frameSize.toLong())
            return StreamSession(seekResult.stream, format)
        }
        val stream = player.pcmStreamFactory(file.toPath())
        val format = stream.format
        player.pcmFormat.set(format)
        val alignedOffset = requestedOffset.coerceAtLeast(0L).alignDown(format.frameSize.toLong())
        player.framePosition = skipFully(stream, alignedOffset).alignDown(format.frameSize.toLong())
        return StreamSession(stream, format)
    }

    private fun openLine(format: AudioFormat): javax.sound.sampled.SourceDataLine {
        val line = player.lineFactory(format)
        // Open the line before publishing it to player.lineRef so a failed line.open never
        // leaves an unusable line referenced by the player (which drainAndClose would then
        // drain/stop/close while it is not open).
        line.open(format, 4096)
        line.start()
        player.lineRef.set(line)
        player.lineStartFrameOffset = player.framePosition
        return line
    }

    internal fun skipFully(stream: AudioInputStream, requestedBytes: Long): Long {
        var remaining = requestedBytes
        var skipped = 0L
        val discard = ByteArray(TRANSFER_BUFFER_SIZE)
        while (remaining > 0L) {
            val s = player.state.get()
            if (s == InternalState.STOPPED || s == InternalState.DISPOSED) break
            val bytesRead = stream.read(discard, 0, minOf(remaining, discard.size.toLong()).toInt())
            if (bytesRead < 0) break
            if (bytesRead == 0) {
                // A 0-byte read is "no data ready yet", not EOF, for SPI-decoded streams; treating
                // it as EOF would reopen short of the target and make fallback seeks land early.
                // Mirror streamPcm()'s backoff (the STOPPED/DISPOSED check above bounds the wait).
                Thread.sleep(ZERO_READ_BACKOFF_MILLIS)
                continue
            }
            skipped += bytesRead
            remaining -= bytesRead
        }
        return skipped
    }

    private fun millisToByteOffset(positionMillis: Long, format: AudioFormat): Long {
        val bytesPerMillis = format.frameRate * format.frameSize / 1000.0
        return (positionMillis * bytesPerMillis).toLong().alignDown(format.frameSize.toLong())
    }

    private fun onPlaybackComplete(wasNaturalEnd: Boolean, halted: Boolean) {
        // Drain only on a natural end so the tail of the track is heard; transport-initiated
        // and errored exits flush to stop output immediately.
        if (wasNaturalEnd) player.drainAndClose() else player.flushAndClose()
        // Stale pump (a newer play() has already started): do not mutate shared state
        // or fire callbacks that belong to the newly started playback.
        if (player.activeSessionId.get() != mySession) return
        player.seekPreviewMillis = -1L
        val item = if (wasNaturalEnd) player.currentAudioItem.getAndSet(null) else null
        if (item != null) {
            player.publisher.emitAsync(Played(item))
            player.onFinishCallback.get()?.run()
        }
        if (wasNaturalEnd) player.onFinishCallback.set(null)
        if (player.state.get() != InternalState.DISPOSED) {
            player.state.set(InternalState.IDLE)
            player.internalStatus.set(if (halted) Status.HALTED else Status.READY)
        }
    }

    private fun Long.alignDown(step: Long): Long = if (step > 0L) this - this % step else this

    private data class StreamSession(val stream: AudioInputStream, val format: AudioFormat)

    private data class Reopened(val session: StreamSession, val line: javax.sound.sampled.SourceDataLine)

    private companion object {
        const val TRANSFER_BUFFER_SIZE = 4096
        const val PAUSE_POLL_MILLIS = 20L
        const val ZERO_READ_BACKOFF_MILLIS = 2L
    }
}