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

package net.transgressoft.commons.music.audio

/**
 * Enumeration of supported audio file types with their file extensions.
 */
enum class AudioFileType(val extension: String) {
    MP3("mp3"),
    M4A("m4a"),
    WAV("wav"),
    FLAC("flac");

    companion object {
        val extensions = entries.map { it.extension }
    }

    override fun toString(): String = extension
}

/**
 * Converts a file extension string to its corresponding [AudioFileType].
 *
 * @throws UnsupportedOperationException if the extension is not supported
 */
fun String.toAudioFileType(): AudioFileType =
    AudioFileType.entries.find { it.extension == this }
        ?: throw UnsupportedOperationException("'$this' is not a supported audio file extension")