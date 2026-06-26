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
 * Unlike [Artist] and [Label], album details instances are not cached because albums are less
 * frequently deduplicated across audio items and the memory savings would be minimal
 * compared to the overhead of maintaining a cache. The [albumArtist] and [label] properties
 * are nested value types.
 *
 * The [UNKNOWN] sentinel represents an album whose metadata is entirely unspecified.
 */
data class AlbumDetails(
    val name: String,
    val albumArtist: Artist = Artist.UNKNOWN,
    val isCompilation: Boolean = false,
    val year: Short? = null,
    val label: Label = Label.UNKNOWN
) : Comparable<AlbumDetails> {

    override fun compareTo(other: AlbumDetails): Int {
        val nameComparison = compareValues(name, other.name)
        val artistComparison = albumArtist.compareTo(other.albumArtist)
        val labelComparison = compareValues(label.name, other.label.name)
        val yearComparison = compareValues(year, other.year)
        val compilationComparison = compareValues(isCompilation, other.isCompilation)
        return when {
            labelComparison != 0 -> labelComparison
            yearComparison != 0 -> yearComparison
            artistComparison != 0 -> artistComparison
            nameComparison != 0 -> nameComparison
            else -> compilationComparison
        }
    }

    companion object {

        @get:JvmName("UNKNOWN")
        val UNKNOWN: AlbumDetails = AlbumDetails("", Artist.UNKNOWN, false, null, Label.UNKNOWN)
    }
}

/**
 * Returns a string key uniquely identifying this album by its full value.
 *
 * Two albums sharing a [name] but differing in album artist, label, year, or compilation flag map to
 * distinct ids, consistent with the structural equality of [AlbumDetails]. Mirrors [Artist.id] and is
 * used as [net.transgressoft.lirp.entity.ReactiveEntityBase.uniqueId] in album implementations.
 *
 * Each component is length-prefixed so that a delimiter character occurring inside a field value
 * cannot produce the same id as a different field decomposition.
 */
fun AlbumDetails.id(): String =
    listOf(
        name,
        albumArtist.id(),
        label.name,
        year?.toString().orEmpty(),
        isCompilation.toString()
    ).joinToString("|") { "${it.length}:$it" }