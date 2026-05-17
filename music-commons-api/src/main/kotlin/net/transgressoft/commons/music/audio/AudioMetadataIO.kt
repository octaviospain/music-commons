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

import org.jaudiotagger.tag.Tag
import java.nio.file.Path

/**
 * Port for reading and writing audio metadata against a file at a given [Path].
 *
 * Production callers wire in the default JAudioTagger-backed implementation; tests inject
 * lightweight fakes so they neither touch the JAudioTagger static `AudioFileIO` nor require real
 * audio bytes on disk. Centralizing the four operations behind this interface eliminates direct
 * JAudioTagger access from audio item implementations.
 */
interface AudioMetadataIO {

    /**
     * Reads the tag block from the audio file at [path].
     *
     * @param path location of the audio file to read
     * @return the populated [Tag] describing the file's metadata fields
     */
    fun readTag(path: Path): Tag

    /**
     * Reads the audio header (codec, bitrate, duration, container format) from [path].
     *
     * Header values are independent of tag content and survive empty or unreadable tag blocks,
     * so this can be called even when [readTag] fails.
     *
     * @param path location of the audio file to inspect
     * @return a [HeaderInfo] snapshot of the file's header fields
     */
    fun readHeaderInfo(path: Path): HeaderInfo

    /**
     * Returns the raw bytes of the first embedded cover artwork, or `null` when the file has no
     * artwork or cannot be read.
     *
     * @param path location of the audio file to inspect
     */
    fun readCoverBytes(path: Path): ByteArray?

    /**
     * Writes [tag] back to the audio file at [path], replacing any existing tag block.
     *
     * Implementations must be idempotent: calling twice with the same tag must yield the same
     * on-disk bytes.
     *
     * @param path location of the audio file to update
     * @param tag the populated tag block to persist
     */
    fun writeTag(path: Path, tag: Tag)
}