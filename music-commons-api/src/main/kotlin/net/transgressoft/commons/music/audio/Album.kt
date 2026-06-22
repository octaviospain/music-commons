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
 * Concrete value type representing album metadata for an audio item.
 *
 * Unlike [Artist] and [Label], album instances are not cached because albums are less
 * frequently deduplicated across audio items and the memory savings would be minimal
 * compared to the overhead of maintaining a cache. The [albumArtist] and [label] properties
 * are nested value types.
 *
 * The [UNKNOWN] sentinel represents an album whose metadata is entirely unspecified.
 */
data class Album(
    val name: String,
    val albumArtist: Artist = Artist.UNKNOWN,
    val isCompilation: Boolean = false,
    val year: Short? = null,
    val label: Label = Label.UNKNOWN
) : Comparable<Album> {

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

    companion object {

        @get:JvmName("UNKNOWN")
        val UNKNOWN: Album = Album("", Artist.UNKNOWN, false, null, Label.UNKNOWN)
    }
}

/**
 * Represents an album as a list of audio items.
 *
 * This interface extends [List] to provide convenient access to all audio items
 * on an album while also exposing the album's name. Instances are typically
 * immutable snapshots of an album's contents at a point in time.
 *
 * @param I The type of audio items in this album set
 */
interface AlbumSet<I : ReactiveAudioItem<I>> : List<I>, Comparable<AlbumSet<I>> {
    /**
     * The name of the album.
     */
    val albumName: String
}