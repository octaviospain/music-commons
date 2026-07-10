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
 * @since 1.0
 */
public data class AlbumDetails(
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

    public companion object {

        @get:JvmName("UNKNOWN")
        public val UNKNOWN: AlbumDetails = AlbumDetails("", Artist.UNKNOWN, false, null, Label.UNKNOWN)
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
 * cannot produce the same id as a different field decomposition. When a stable entity identifier
 * that is resilient to year/label variance is needed, use [canonicalKey] first: [canonicalKey].id()
 * encodes only the identity components (normalized name + compilation-aware artist).
 *
 * @see canonicalKey
 * @since 1.0
 */
public fun AlbumDetails.id(): String =
    listOf(
        name,
        albumArtist.id(),
        label.name,
        year?.toString().orEmpty(),
        isCompilation.toString()
    ).joinToString("|") { "${it.length}:$it" }

/**
 * Returns whether this album is a compilation for identity purposes.
 *
 * Treats blank [albumArtist], [Artist.UNKNOWN], and "Various Artists" (case-insensitive)
 * as equivalent to [isCompilation] == true. Tracks with these album-artist values
 * share a compilation bucket regardless of their [isCompilation] flag. This intentional
 * overlap means "no album artist" tracks merge with compilations of the same normalized name.
 * @since 1.0
 */
public fun AlbumDetails.isCompilationAlbum(): Boolean =
    isCompilation ||
        albumArtist == Artist.UNKNOWN ||
        albumArtist.name.isBlank() ||
        albumArtist.name.trim().equals("Various Artists", ignoreCase = true)

/**
 * Returns a canonical [AlbumDetails] used as the projection bucket key.
 *
 * Identity is [name] (lowercase, trimmed, whitespace-collapsed) plus a compilation-aware album
 * artist: when the album is a compilation (see [isCompilationAlbum]), the artist component
 * collapses to [Artist.UNKNOWN] so all tracks that belong to the same compilation merge into one
 * bucket regardless of their per-track [albumArtist] value. For a non-compilation album the artist
 * name is normalized the same way as [name] (lowercase, trimmed, whitespace-collapsed) so that
 * per-track casing/whitespace variance in the album artist does not fragment the bucket, while
 * genuinely different artist names stay distinct. [year], [label], and [isCompilation] are zeroed
 * so per-track variance in those fields does not fragment the bucket.
 * This function is idempotent: calling it on its own result produces an equal value.
 *
 * The canonical key is NOT equal to the bucket's representative [AlbumDetails] (which preserves
 * original casing and carries the most-frequent year/label values). Use [canonicalKey] only for
 * projection indexing and lookup; expose the representative to consumers.
 * @since 1.0
 */
public fun AlbumDetails.canonicalKey(): AlbumDetails =
    AlbumDetails(
        name = name.trim().replace(Regex("\\s+"), " ").lowercase(),
        albumArtist =
            if (isCompilationAlbum()) {
                Artist.UNKNOWN
            } else {
                Artist.of(albumArtist.name.trim().replace(Regex("\\s+"), " ").lowercase(), albumArtist.countryCode)
            },
        isCompilation = false,
        year = null,
        label = Label.UNKNOWN
    )