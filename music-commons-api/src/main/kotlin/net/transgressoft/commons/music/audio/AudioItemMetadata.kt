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

import com.neovisionaries.i18n.CountryCode
import java.time.Duration

/**
 * Public value-object carrying tag-derived metadata for a single audio file.
 *
 * Returned by metadata reader components in `music-commons-core` and consumed by audio item
 * implementations as the seed state for their reactive properties. Header-derived fields
 * ([bitRate], [duration]) originate from the metadata IO layer, while the remaining tag fields
 * are tag-derived. The [coverBytes] field is populated by the audio library's file-loading path
 * when constructing fresh items and left `null` when the value is hydrated from JSON
 * deserialization, where covers travel out-of-band.
 *
 * Defaults route through [UnknownArtist] / [UnknownAlbum]; core's `ImmutableArtist.UNKNOWN`,
 * `ImmutableAlbum.UNKNOWN` and `ImmutableLabel.UNKNOWN` are aliases for the same singletons so
 * there is a single physical "unknown" instance per type across the library.
 *
 * Serialization stays at the entity level (see core's audio item serializer); this type is not
 * `@Serializable` because its consumers serialize the broader audio item rather than the metadata
 * value in isolation.
 */
data class AudioItemMetadata(
    val title: String = "",
    val artist: Artist = UnknownArtist,
    val album: Album = UnknownAlbum,
    val genres: Set<Genre> = emptySet(),
    val comments: String? = null,
    val trackNumber: Short? = null,
    val discNumber: Short? = null,
    val bpm: Float? = null,
    val encoder: String? = null,
    val encoding: String? = null,
    val bitRate: Int = 0,
    val duration: Duration = Duration.ZERO,
    val coverBytes: ByteArray? = null
)

/**
 * Api-resident singleton sentinel for an unknown [Artist].
 *
 * Reused by core's `ImmutableArtist.UNKNOWN`, by [AudioItemMetadata.artist] defaulting, and by
 * the flyweight cache in `ImmutableArtist.of("")` so all paths return the exact same instance.
 */
object UnknownArtist : Artist {
    override val name: String = ""
    override val countryCode: CountryCode = CountryCode.UNDEFINED

    override fun compareTo(other: Artist): Int {
        val nameComparison = compareValues(name, other.name)
        return if (nameComparison == 0) compareValues(countryCode, other.countryCode) else nameComparison
    }

    override fun toString(): String = "Artist(name=$name, countryCode=${countryCode.name})"
}

/**
 * Api-resident singleton sentinel for an unknown [Label].
 *
 * Reused by core's `ImmutableLabel.UNKNOWN`, by [UnknownAlbum.label], and by the flyweight cache
 * in `ImmutableLabel.of("")` so all paths return the exact same instance.
 */
object UnknownLabel : Label {
    override val name: String = ""
    override val countryCode: CountryCode = CountryCode.UNDEFINED

    override fun compareTo(other: Label): Int {
        val nameComparison = compareValues(name, other.name)
        return if (nameComparison == 0) compareValues(countryCode, other.countryCode) else nameComparison
    }

    override fun toString(): String = "Label(name=$name, countryCode=${countryCode.name})"
}

/**
 * Api-resident singleton sentinel for an unknown [Album].
 *
 * Reused by core's `ImmutableAlbum.UNKNOWN` and by [AudioItemMetadata.album] defaulting so all
 * paths return the exact same instance.
 */
object UnknownAlbum : Album {
    override val name: String = ""
    override val albumArtist: Artist = UnknownArtist
    override val isCompilation: Boolean = false
    override val year: Short? = null
    override val label: Label = UnknownLabel

    override fun compareTo(other: Album): Int {
        val nameComparison = compareValues(name, other.name)
        return if (nameComparison == 0) compareValues(albumArtist.name, other.albumArtist.name) else nameComparison
    }

    override fun toString(): String = "Album(name=$name, albumArtist=$albumArtist, isCompilation=$isCompilation, year=$year, label=$label)"
}