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
 * Interface for playing audio items with playback controls and status monitoring.
 *
 * This interface publishes [AudioItemPlayerEvent] to notify subscribers of player state changes.
 */
interface AudioItemPlayer : Flow.Publisher<AudioItemPlayerEvent> {
    /**
     * Represents the possible states of the audio player.
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

    val totalDuration: Duration

    fun status(): Status

    fun getCurrentTime(): Duration

    @Throws(UnsupportedAudioPlaybackException::class)
    fun play(audioItem: ReactiveAudioItem<*>)

    fun pause()

    fun resume()

    fun stop()

    fun dispose()

    fun setVolume(value: Double)

    fun seek(position: Duration)

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
            val encoder = audioItem.encoder ?: ""

            return when {
                // ALAC in M4A
                encoding.startsWith("Apple", ignoreCase = true) || encoder.startsWith("iTunes", ignoreCase = true) -> false
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
            val encoder = audioItem.encoder ?: ""

            return when {
                encoding.startsWith("AAC", ignoreCase = true) || encoding.contains("MPEG-4", ignoreCase = true) -> AudioFileCodec.AAC
                encoding.startsWith("Apple", ignoreCase = true) || encoder.startsWith("iTunes", ignoreCase = true) -> AudioFileCodec.ALAC
                encoding.startsWith("Opus", ignoreCase = true) -> AudioFileCodec.OPUS
                encoding.startsWith("FLAC", ignoreCase = true) && fileType == AudioFileType.M4A -> AudioFileCodec.FLAC
                else -> fileType.primaryCodec
            }
        }
    }
}