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

import net.transgressoft.commons.util.toJsonUri
import net.transgressoft.lirp.entity.ReactiveEntity
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Represents a reactive audio item with comprehensive metadata and file information.
 *
 * This interface extends [ReactiveEntity] to provide reactive capabilities for audio items,
 * allowing observation of changes to metadata and other properties.
 */
interface ReactiveAudioItem<I: ReactiveAudioItem<I>>: ReactiveEntity<Int, I>, Comparable<I> {

    /** Absolute path to the audio file on disk. */
    val path: Path

    /** File name including extension, derived from [path]. */
    val fileName: String

    /** File extension without the leading dot (e.g. `"mp3"`, `"flac"`). */
    val extension: String

    /** Track title as stored in the file's tag. */
    var title: String

    /** Playback duration of the audio file. */
    val duration: Duration

    /** Bit rate of the audio stream in kbps. */
    val bitRate: Int

    /** Primary credited artist for this track. */
    var artist: Artist

    /**
     * All artists involved in this track, including the primary [artist] and any featured or
     * contributing artists encoded in the tag.
     */
    val artistsInvolved: Set<Artist>

    /** Album to which this track belongs, including album-level metadata. */
    var album: AlbumDetails

    /** Set of genres associated with this track. An empty set means untagged. */
    var genres: Set<Genre>

    /** Free-text comment stored in the tag, or `null` if absent. */
    var comments: String?

    /** Track number within its disc, or `null` if not tagged. */
    var trackNumber: Short?

    /** Disc number within the album, or `null` if not tagged. */
    var discNumber: Short?

    /** Beats per minute, or `null` if not tagged. */
    var bpm: Float?

    /** Encoder tool or settings string from the tag, or `null` if absent. */
    val encoder: String?

    /** Audio encoding format identifier (e.g. codec name), or `null` if absent. */
    val encoding: String?

    /** File size in bytes. */
    val length: Long

    /**
     * The cover image bytes for this audio item, or `null` if no cover image is present.
     *
     * The returned reference points directly to the internal array — callers must treat it
     * as immutable and must not modify its contents. Implementations may load the bytes
     * lazily on first access; subsequent reads return the cached reference. All mutations
     * must go through the setter so reactive change notifications are published.
     */
    var coverImageBytes: ByteArray?

    /** Timestamp when the track entry was first created in the library. */
    val dateOfCreation: LocalDateTime

    /** Number of times this track has been played. */
    val playCount: Short

    /**
     * Sets the play count to [count].
     *
     * Used during import operations (e.g., iTunes import) where the play count is transferred
     * from an external source rather than incremented through playback. Implementations disable
     * reactive mutation events during this operation since it represents a bulk data transfer,
     * not a user-observable state change.
     *
     * @param count The play count value to set.
     */
    fun setPlayCount(count: Short)

    /**
     * Applies multiple field mutations atomically, emitting a single [net.transgressoft.lirp.event.BatchChanged]
     * event at the end of the block instead of one [net.transgressoft.lirp.event.PropertyChanged] per field.
     *
     * Use when updating two or more catalog-relevant properties (artist, album, genres) together,
     * e.g. during import operations, so downstream projections re-key once rather than once per field.
     *
     * @param action The mutation block receiving this item as receiver.
     */
    fun mutate(action: I.() -> Unit)

    /**
     * Returns a JSON string with this item's numeric ID as the key and its full metadata object as the value.
     *
     * @return JSON object string suitable for embedding in a larger JSON map
     */
    fun asJsonKeyValue() =
        buildJsonObject {
            put("$id", toJsonObject())
        }.toString()

    /**
     * Returns a JSON string containing only this item's metadata object, without a surrounding key.
     *
     * @return JSON object string of this item's full metadata
     */
    fun asJsonValue() = toJsonObject().toString()
}

/**
 * Converts a reactive audio item to a [JsonObject] containing all metadata.
 */
fun ReactiveAudioItem<*>.toJsonObject(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("path", path.toJsonUri())
        put("title", title)
        put("duration", duration.toSeconds())
        put("bitRate", bitRate)
        put(
            "artist",
            buildJsonObject {
                put("name", artist.name)
                put("countryCode", artist.countryCode.name)
            }
        )
        put(
            "album",
            buildJsonObject {
                put("name", album.name)
                put(
                    "albumArtist",
                    buildJsonObject {
                        put("name", album.albumArtist.name)
                        put("countryCode", album.albumArtist.countryCode.name)
                    }
                )
                put("isCompilation", album.isCompilation)
                put("year", album.year)
                put(
                    "label",
                    buildJsonObject {
                        put("name", album.label.name)
                        put("countryCode", album.label.countryCode.name)
                    }
                )
            }
        )
        put("genres", JsonArray(genres.map { it.name }.sorted().map(::JsonPrimitive)))
        put("comments", comments)
        put("trackNumber", trackNumber)
        put("discNumber", discNumber)
        put("bpm", bpm)
        put("encoder", encoder)
        put("encoding", encoding)
        put("dateOfCreation", dateOfCreation.toEpochSecond(ZoneOffset.UTC))
        put("lastDateModified", lastDateModified.toEpochSecond(ZoneOffset.UTC))
        put("playCount", playCount)
    }