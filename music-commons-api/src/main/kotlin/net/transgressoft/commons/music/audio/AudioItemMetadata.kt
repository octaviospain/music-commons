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
) {
    init {
        bpm?.let {
            require(it.isFinite()) { "bpm must be a finite Float (got $it); NaN and ±Infinity are not representable in JSON" }
        }
    }

    // ByteArray uses reference equality by default, breaking value-object semantics for the cover-bytes
    // field. Override equals/hashCode so two metadata instances with byte-identical covers are equal.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioItemMetadata) return false
        return title == other.title &&
            artist == other.artist &&
            album == other.album &&
            genres == other.genres &&
            comments == other.comments &&
            trackNumber == other.trackNumber &&
            discNumber == other.discNumber &&
            bpm == other.bpm &&
            encoder == other.encoder &&
            encoding == other.encoding &&
            bitRate == other.bitRate &&
            duration == other.duration &&
            (coverBytes?.contentEquals(other.coverBytes) ?: (other.coverBytes == null))
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + genres.hashCode()
        result = 31 * result + (comments?.hashCode() ?: 0)
        result = 31 * result + (trackNumber?.hashCode() ?: 0)
        result = 31 * result + (discNumber?.hashCode() ?: 0)
        result = 31 * result + (bpm?.hashCode() ?: 0)
        result = 31 * result + (encoder?.hashCode() ?: 0)
        result = 31 * result + (encoding?.hashCode() ?: 0)
        result = 31 * result + bitRate
        result = 31 * result + duration.hashCode()
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        return result
    }
}

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