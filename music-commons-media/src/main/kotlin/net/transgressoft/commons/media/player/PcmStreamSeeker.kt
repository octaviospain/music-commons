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

import java.io.File

/**
 * Contract for per-format PCM stream seekers used by the player's seek chain.
 *
 * Implementations open a decoded PCM stream positioned at the requested decoded byte offset
 * for a specific audio format. When a seeker does not handle the given file's format or cannot
 * seek to the requested offset, it returns null, allowing the caller to try the next seeker in
 * the chain and ultimately fall through to the full-decode byte-skip path.
 */
internal fun interface PcmStreamSeeker {

    /**
     * Attempts to open a decoded PCM stream positioned at [requestedByteOffset] for the given [file].
     *
     * Returns null if this seeker does not handle the file's format or cannot seek to the
     * requested offset, allowing the caller to try the next seeker in the chain.
     *
     * @param file the audio file to open
     * @param requestedByteOffset the desired decoded PCM byte offset to seek to
     * @return a [SeekablePcmStream] with the stream and actual start offset, or null if this seeker cannot handle the request
     */
    fun open(file: File, requestedByteOffset: Long): SeekablePcmStream?
}