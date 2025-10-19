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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Immutable [Album] implementation representing album metadata.
 *
 * Unlike [ImmutableArtist] and [ImmutableLabel], album instances are not cached since
 * albums are less frequently duplicated across audio items and the memory savings
 * would be minimal compared to the overhead of maintaining a cache.
 */
@Serializable
@SerialName("ImmutableAlbum")
class ImmutableAlbum(
    override val name: String,
    override val albumArtist: Artist,
    override val isCompilation: Boolean = false,
    override val year: Short? = null,
    override val label: Label = ImmutableLabel.UNKNOWN
) : Album {

    override fun compareTo(other: Album): Int {
        val nameComparison = compareValues(name, other.name)
        val artistComparison = compareValues(albumArtist.name, other.albumArtist.name)
        val labelComparison = compareValues(label.name, other.label.name)
        val yearComparison = compareValues(year, other.year)
        return when {
            labelComparison != 0 -> labelComparison
            yearComparison != 0 -> yearComparison
            artistComparison != 0 -> artistComparison
            else -> nameComparison
        }
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + albumArtist.hashCode()
        result = 31 * result + isCompilation.hashCode()
        result = 31 * result + year.hashCode()
        result = 31 * result + label.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutableAlbum

        if (name != other.name) return false
        if (albumArtist != other.albumArtist) return false
        if (isCompilation != other.isCompilation) return false
        if (year != other.year) return false
        return label == other.label
    }

    override fun toString() = "ImmutableAlbum(name='$name', albumArtist=$albumArtist, isCompilation=$isCompilation, year=$year, label=$label)"

    companion object {
        @get:JvmName("UNKNOWN")
        val UNKNOWN = ImmutableAlbum("", ImmutableArtist.UNKNOWN)
    }
}