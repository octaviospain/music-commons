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

fun <R: ReactiveAudioItem<*>> Collection<R>.asJsonKeyValues(): String =
    buildJsonObject {
        forEach {
            it.asJsonKeyValue()
        }
    }.toString()

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