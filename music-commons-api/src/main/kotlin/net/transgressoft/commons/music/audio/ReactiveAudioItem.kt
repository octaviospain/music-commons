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

import net.transgressoft.commons.entity.ReactiveEntity
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Represents a reactive audio item with comprehensive metadata and file information.
 *
 * This interface extends [ReactiveEntity] to provide reactive capabilities for audio items,
 * allowing observation of changes to metadata and other properties.
 */
interface ReactiveAudioItem<I: ReactiveAudioItem<I>>: ReactiveEntity<Int, I> {

    val path: Path
    val fileName: String
    val extension: String
    var title: String
    val duration: Duration
    val bitRate: Int
    var artist: Artist
    val artistsInvolved: Set<Artist>
    var album: Album
    var genre: Genre
    var comments: String?
    var trackNumber: Short?
    var discNumber: Short?
    var bpm: Float?
    val encoder: String?
    val encoding: String?
    val length: Long
    var coverImageBytes: ByteArray?
    val dateOfCreation: LocalDateTime
    val playCount: Short

    fun writeMetadata(): Job

    fun asJsonKeyValue() =
        buildJsonObject {
            put("$id", toJsonObject())
        }.toString()

    fun asJsonValue() = toJsonObject().toString()
}

/**
 * Converts a collection of reactive audio items to a JSON string with items as key-value pairs.
 */
fun <R: ReactiveAudioItem<*>> Collection<R>.asJsonKeyValues(): String =
    buildJsonObject {
        forEach {
            it.asJsonKeyValue()
        }
    }.toString()

/**
 * Converts a reactive audio item to a [JsonObject] containing all metadata.
 */
fun ReactiveAudioItem<*>.toJsonObject(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("path", path.absolutePathString())
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
                    }
                )
                put("isCompilation", album.isCompilation)
                put("year", album.year)
                put(
                    "label",
                    buildJsonObject {
                        put("name", album.label.name)
                    }
                )
            }
        )
        put("genre", genre.name)
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