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

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import java.nio.file.FileSystems
import java.nio.file.Path

/**
 * Production [AudioMetadataIO] backed by JAudioTagger's [AudioFileIO].
 *
 * Fails fast when handed a [Path] from a non-default filesystem provider (e.g. Jimfs):
 * JAudioTagger reads through `java.io.File`, and non-default-FS paths throw
 * `UnsupportedOperationException` from `path.toFile()`. Tests that need to operate
 * against an in-memory filesystem must inject [FakeAudioMetadataIO] instead — the
 * guard makes the mismatch surface with a helpful message rather than the cryptic
 * upstream exception.
 */
internal class DefaultAudioMetadataIO : AudioMetadataIO {

    override fun readTag(path: Path): Tag {
        requireDefaultFileSystem(path)
        return AudioFileIO.read(path.toFile()).tag
    }

    override fun readHeaderInfo(path: Path): HeaderInfo {
        requireDefaultFileSystem(path)
        val header = AudioFileIO.read(path.toFile()).audioHeader
        return HeaderInfo(
            encodingType = header.encodingType,
            bitRate = parseBitRate(header.bitRate),
            trackLengthSeconds = header.trackLength.toLong(),
            format = header.format
        )
    }

    override fun readCoverBytes(path: Path): ByteArray? {
        requireDefaultFileSystem(path)
        val file = path.toFile()
        if (!file.exists() || !file.canRead()) return null
        return runCatching { AudioFileIO.read(file).tag }
            .getOrNull()
            ?.let { tag ->
                if (tag.artworkList.isNotEmpty()) tag.firstArtwork.binaryData else null
            }
    }

    override fun writeTag(path: Path, tag: Tag) {
        requireDefaultFileSystem(path)
        val audioFile = AudioFileIO.read(path.toFile())
        audioFile.tag = tag
        audioFile.commit()
    }

    private fun requireDefaultFileSystem(path: Path) {
        require(path.fileSystem == FileSystems.getDefault()) {
            "DefaultAudioMetadataIO does not support non-default filesystems (got ${path.fileSystem}). " +
                "Use FakeAudioMetadataIO for Jimfs paths."
        }
    }

    private fun parseBitRate(bitRate: String): Int =
        if (bitRate.startsWith("~")) bitRate.substring(1).toInt() else bitRate.toInt()
}