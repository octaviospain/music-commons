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

import net.transgressoft.commons.media.util.readAudioFileFormat
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import java.io.File
import java.nio.file.Path
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioInputStream

/**
 * Resolves the playable duration of an audio file, handling formats where the container-level
 * frame count is absent or unreliable (AAC/M4A in particular).
 *
 * Pure duration-resolution logic extracted from the player so that the player's own state
 * management is not entangled with potentially blocking I/O. The caller (player) keeps probe
 * thread management and session-id guarding.
 *
 * @param pcmStreamFactory opens a decoded PCM [AudioInputStream] for the given [Path]; used
 *   by the full-decode fallback when no frame-length is available in the container header
 */
internal class DurationProber(
    private val pcmStreamFactory: (Path) -> AudioInputStream
) {

    /**
     * Returns the duration in milliseconds for a file whose container format is already known.
     * Falls back to zero if neither the [AudioFileFormat] duration property nor the frame-count
     * is available.
     *
     * @param audioFileFormat the container-level format descriptor
     * @return duration in milliseconds, or 0 if not determinable
     */
    fun durationMillis(audioFileFormat: AudioFileFormat): Long {
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

    /**
     * Returns true when the container reports an MP3 codec inside an AAC/M4A/MP4 container,
     * which is a known condition where the container frame count is unreliable. In such cases,
     * a full-decode probe is required to obtain an accurate duration.
     *
     * @param file the audio file to check
     * @param audioFileFormat the container-level format descriptor
     * @return true if a decoded-stream probe is required to determine duration accurately
     */
    fun requiresDecodedDurationProbe(file: File, audioFileFormat: AudioFileFormat): Boolean {
        val containerExtension = file.extension.lowercase()
        if (containerExtension !in setOf("m4a", "mp4", "aac")) {
            return false
        }
        return audioFileFormat.type.extension.equals("mp3", ignoreCase = true)
    }

    /**
     * Attempts to resolve the playable duration for [file], falling back to a full PCM-decode
     * probe when the container format is unavailable or when [requiresDecodedDurationProbe]
     * returns true.
     *
     * @param file the audio file whose duration should be determined
     * @param fileName display name used in exception messages (typically the file name only)
     * @return duration in milliseconds
     * @throws UnsupportedAudioPlaybackException if neither container nor decoded-stream
     *   duration can be determined (unsupported format)
     */
    fun resolvePlayableDurationMillis(file: File, fileName: String): Long {
        val audioFileFormat =
            try {
                readAudioFileFormat(file.toPath())
            } catch (formatFailure: Exception) {
                return resolveDurationFromDecodedPcmOrThrow(file, fileName, formatFailure)
            }
        return resolveSourceDurationMillis(file, audioFileFormat)
    }

    private fun resolveSourceDurationMillis(file: File, audioFileFormat: AudioFileFormat): Long {
        val fileFormatDurationMillis = durationMillis(audioFileFormat)
        if (!requiresDecodedDurationProbe(file, audioFileFormat)) {
            return fileFormatDurationMillis
        }

        return try {
            val decodedDurationMillis = durationMillisFromPcmStream(file)
            if (decodedDurationMillis > 0L) decodedDurationMillis else fileFormatDurationMillis
        } catch (e: Exception) {
            fileFormatDurationMillis
        }
    }

    private fun resolveDurationFromDecodedPcmOrThrow(file: File, fileName: String, formatFailure: Exception): Long =
        try {
            durationMillisFromPcmStream(file)
        } catch (decodeFailure: Exception) {
            throw UnsupportedAudioPlaybackException("Cannot play audio item '$fileName': unsupported format", decodeFailure).apply {
                addSuppressed(formatFailure)
            }
        }

    private fun durationMillisFromPcmStream(file: File): Long {
        val stream = pcmStreamFactory(file.toPath())
        return stream.use { s ->
            val format = s.format
            val frameSize = format.frameSize.takeIf { it > 0 } ?: return 0L
            val frameRate = format.frameRate
            if (frameRate <= 0f) return 0L

            val buffer = ByteArray(TRANSFER_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val bytesRead = s.read(buffer)
                if (bytesRead <= 0) break
                totalBytes += bytesRead
            }
            (totalBytes * 1000.0 / frameSize / frameRate).toLong()
        }
    }

    private companion object {
        const val TRANSFER_BUFFER_SIZE = 4096
    }
}