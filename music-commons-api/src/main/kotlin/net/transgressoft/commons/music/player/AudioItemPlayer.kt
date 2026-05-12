/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.music.player

import net.transgressoft.commons.music.audio.AudioFileCodec
import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.toAudioFileType
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import java.time.Duration
import java.util.concurrent.Flow

/**
 * Plays a single [ReactiveAudioItem] at a time, exposing transport controls (play, pause,
 * resume, stop, seek), volume, and a [Status] lifecycle that callers can observe via
 * [status] or by subscribing as a [Flow.Subscriber] to the [AudioItemPlayerEvent] stream
 * this interface publishes.
 *
 * Implementations are expected to be safe to use from a single coordinating thread; UI
 * adapters such as the JavaFX wrapper republish state changes on the appropriate thread
 * for their toolkit. Decoding and audio output run off-thread so that transport calls do
 * not block the caller.
 *
 * A natural end-of-track triggers an [AudioItemPlayerEvent.Played] event and any callback
 * registered via [onFinish]; transport-initiated stops and decode errors do not.
 */
interface AudioItemPlayer : Flow.Publisher<AudioItemPlayerEvent> {
    /**
     * Lifecycle states reported by [status]. Implementations move through these states in
     * response to transport calls and to playback progress.
     *
     * - [UNKNOWN]: initial state before any audio item has been loaded.
     * - [READY]: idle and ready to accept a [play] call (e.g. after construction with an
     *   item loaded or after a natural end of track).
     * - [PLAYING]: actively decoding and writing audio to the output line.
     * - [PAUSED]: playback suspended with position preserved; [resume] continues from here.
     * - [STOPPED]: playback halted by an explicit [stop] call; position is reset to 0 and the
     *   next [play] begins from the start of the audio item.
     * - [STALLED]: playback is starved of decoded data (e.g. buffer underrun in a streaming
     *   pipeline). Not used by the current in-memory implementation; reserved for streaming
     *   decoders.
     * - [HALTED]: playback ended because of an unrecoverable decode or I/O error. The
     *   player must be re-initialized with a new [play] call to recover.
     * - [DISPOSED]: terminal state after [dispose]; all further transport calls are
     *   silently ignored (no-ops, no exception thrown).
     */
    enum class Status {
        UNKNOWN,
        READY,
        PAUSED,
        PLAYING,
        STOPPED,
        STALLED,
        HALTED,
        DISPOSED
    }

    /**
     * Total duration of the currently loaded audio item, or [Duration.ZERO] when nothing
     * has been loaded yet or when the duration is not yet known (e.g. before decoding has
     * read enough of the source to determine length).
     */
    val totalDuration: Duration

    /** Returns the current [Status] of the player. */
    fun status(): Status

    /**
     * Returns the playback position of the currently loaded audio item. Returns
     * [Duration.ZERO] when no item is loaded, before playback has produced any output,
     * or after [stop].
     */
    fun getCurrentTime(): Duration

    /**
     * Begins playing [audioItem] from the start. If another item is currently playing or
     * paused, it is stopped first. Implementations validate that the file exists and that
     * its format is supported before starting playback.
     *
     * @param audioItem the audio item to play
     * @throws UnsupportedAudioPlaybackException if the item's file is missing or its
     *   format cannot be decoded
     */
    @Throws(UnsupportedAudioPlaybackException::class)
    fun play(audioItem: ReactiveAudioItem<*>)

    /**
     * Suspends playback while preserving the current position. Has no effect if the
     * player is not in [Status.PLAYING]. Call [resume] to continue.
     */
    fun pause()

    /**
     * Continues playback from the position at which it was paused. Has no effect if the
     * player is not in [Status.PAUSED].
     */
    fun resume()

    /**
     * Halts playback and resets the position to the start of the current audio item. The
     * loaded item is retained, so a subsequent [play] call (with the same or a different
     * item) begins from the beginning.
     */
    fun stop()

    /**
     * Releases all underlying resources (audio output line, decoder buffers, pump
     * threads). Idempotent: calling more than once is safe. After disposal the player
     * transitions to [Status.DISPOSED] and silently ignores any further transport calls.
     */
    fun dispose()

    /**
     * Sets the playback volume in the range `[0.0, 1.0]`. Values outside the range are
     * clamped. Takes effect on subsequent audio output.
     */
    fun setVolume(value: Double)

    /**
     * Requests a seek to [position] within the currently loaded audio item. The seek is
     * applied as soon as the pump thread observes the request; callers should not assume
     * synchronous repositioning. Positions outside the track length are clamped by the
     * implementation.
     */
    fun seek(position: Duration)

    /**
     * Registers a callback to be invoked exactly once when the currently loaded audio
     * item reaches its natural end of stream. The callback does not fire for
     * transport-initiated stops, errors, or when [dispose] is called. Setting a new
     * callback replaces any previously registered one.
     */
    fun onFinish(value: Runnable)

    companion object {
        private val PLAYABLE_FILE_TYPES = AudioFileType.entries.filter { it.isPrimaryCodecSupported() }.toSet()

        /**
         * Determines if an audio item is playable based on its file type and codec.
         *
         * Checks both the file extension against known playable types and the
         * encoding/encoder metadata to filter out unsupported codecs (ALAC, Opus,
         * FLAC-in-M4A).
         *
         * @param audioItem the audio item to check
         * @return true if the item's format and codec are supported for playback
         */
        fun isPlayable(audioItem: ReactiveAudioItem<*>): Boolean {
            val fileType = runCatching { audioItem.extension.toAudioFileType() }.getOrNull()
            if (fileType !in PLAYABLE_FILE_TYPES) return false

            val encoding = audioItem.encoding ?: ""

            return when {
                // ALAC in M4A — identified by the codec-specific encoding string only.
                // The encoder/muxer tool name (e.g. "iTunes") is not a reliable signal: a
                // normal AAC file can also be produced by iTunes.
                encoding.startsWith("Apple", ignoreCase = true) -> false
                // Opus in any container
                encoding.startsWith("Opus", ignoreCase = true) -> false
                // FLAC in M4A container (not native FLAC files)
                encoding.startsWith("FLAC", ignoreCase = true) && fileType == AudioFileType.M4A -> false
                else -> true
            }
        }

        /**
         * Returns the detected [AudioFileCodec] for the given audio item, or null if
         * the codec cannot be determined from the file type and metadata.
         *
         * @param audioItem the audio item to analyze
         * @return the detected codec or null
         */
        fun detectCodec(audioItem: ReactiveAudioItem<*>): AudioFileCodec? {
            val fileType = runCatching { audioItem.extension.toAudioFileType() }.getOrNull() ?: return null
            val encoding = audioItem.encoding ?: ""

            return when {
                encoding.startsWith("AAC", ignoreCase = true) || encoding.startsWith("MPEG-4", ignoreCase = true) -> AudioFileCodec.AAC
                // ALAC is identified by the codec-specific encoding string only;
                // a normal AAC M4A from iTunes has encoder = "iTunes..." too.
                encoding.startsWith("Apple", ignoreCase = true) -> AudioFileCodec.ALAC
                encoding.startsWith("Opus", ignoreCase = true) -> AudioFileCodec.OPUS
                encoding.startsWith("FLAC", ignoreCase = true) && fileType == AudioFileType.M4A -> AudioFileCodec.FLAC
                else -> fileType.primaryCodec
            }
        }
    }
}