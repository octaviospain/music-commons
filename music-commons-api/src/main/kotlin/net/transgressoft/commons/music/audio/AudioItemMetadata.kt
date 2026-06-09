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

import net.transgressoft.lirp.persistence.Embeddable
import net.transgressoft.lirp.persistence.PersistenceIgnore
import net.transgressoft.lirp.persistence.PersistenceProperty
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
 * Annotated as `@Embeddable` for SQL persistence via lirp, but it contributes only the
 * file-header-derived columns ([bitRate], [duration], [encoder], [encoding]) when embedded by an
 * entity. Those fields are immutable after construction, so embedding them carries no risk of
 * diverging from live entity state. Every tag field that is *mutable* on the owning entity
 * ([title], [artist], [album], [genres], [comments], [trackNumber], [discNumber], [bpm]) is
 * `@PersistenceIgnore`d here: the entity persists those values directly as its own columns, which
 * is the single source of truth for mutations. [coverBytes] is also `@PersistenceIgnore`d and
 * travels out-of-band.
 *
 * Serialization stays at the entity level (see core's audio item serializer); this type is not
 * `@Serializable` because its consumers serialize the broader audio item rather than the metadata
 * value in isolation.
 */
@Embeddable
data class AudioItemMetadata(
    @PersistenceIgnore val title: String = "",
    @PersistenceIgnore val artist: Artist = Artist.UNKNOWN,
    @PersistenceIgnore val album: Album = Album.UNKNOWN,
    @PersistenceIgnore val genres: Set<Genre> = emptySet(),
    @PersistenceIgnore val comments: String? = null,
    @PersistenceIgnore val trackNumber: Short? = null,
    @PersistenceIgnore val discNumber: Short? = null,
    @PersistenceIgnore val bpm: Float? = null,
    val encoder: String? = null,
    val encoding: String? = null,
    val bitRate: Int = 0,
    @PersistenceProperty(converter = DurationConverter::class) val duration: Duration = Duration.ZERO,
    @PersistenceIgnore val coverBytes: ByteArray? = null
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