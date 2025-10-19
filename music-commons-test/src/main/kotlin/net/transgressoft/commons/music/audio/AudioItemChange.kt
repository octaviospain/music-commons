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

import net.transgressoft.commons.entity.IdentifiableEntity

/**
 * Represents the exhaustive set of fields that are mutable in an audio item.
 */
data class AudioItemChange(
    override val id: Int,
    var title: String? = null,
    var artist: Artist? = null,
    var albumName: String? = null,
    var albumArtist: Artist? = null,
    var isCompilation: Boolean? = null,
    var year: Short? = null,
    var label: Label? = null,
    var genre: Genre? = null,
    var comments: String? = null,
    var trackNumber: Short? = null,
    var discNumber: Short? = null,
    var bpm: Float? = null,
    var coverImageBytes: ByteArray? = null,
    var playCount: Short? = null
) : IdentifiableEntity<Int> {

    override val uniqueId: String = id.toString()

    var album: Album? = null
        set(value) {
            field = value
            albumName = value?.name
            albumArtist = value?.albumArtist
            isCompilation = value?.isCompilation
            year = value?.year
            label = value?.label
        }

    override fun clone(): AudioItemChange = copy()
}