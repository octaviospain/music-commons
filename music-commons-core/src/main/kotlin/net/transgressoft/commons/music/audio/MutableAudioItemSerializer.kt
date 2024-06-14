package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

internal object MutableAudioItemSerializer : KSerializer<MutableAudioItem> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AudioItem") {
        element<String>("id")
        element<String>("path")
    }

    override fun deserialize(decoder: Decoder): MutableAudioItem {
        val jsonInput = decoder as? JsonDecoder ?: throw SerializationException("This class can be saved only by Json")
        val jsonObject = jsonInput.decodeJsonElement().jsonObject

        val id = jsonObject["id"]!!.jsonPrimitive.int
        val path = Path(jsonObject["path"]!!.jsonPrimitive.content)
        val title = jsonObject["title"]!!.jsonPrimitive.content
        val duration = Duration.ofSeconds(jsonObject["duration"]!!.jsonPrimitive.long)
        val bitRate = jsonObject["bitRate"]!!.jsonPrimitive.int
        val artistName = jsonObject["artist"]!!.jsonObject["name"]!!.jsonPrimitive.content
        val artistCountryCode = jsonObject["artist"]!!.jsonObject["countryCode"]!!.jsonPrimitive.content
        val albumName = jsonObject["album"]!!.jsonObject["name"]!!.jsonPrimitive.content
        val albumArtistName = jsonObject["album"]!!.jsonObject["albumArtist"]!!.jsonObject["name"]!!.jsonPrimitive.content
        val isCompilation = jsonObject["album"]!!.jsonObject["isCompilation"]!!.jsonPrimitive.boolean
        val year = jsonObject["album"]!!.jsonObject["year"]!!.jsonPrimitive.int
        val label = jsonObject["album"]!!.jsonObject["label"]!!.jsonObject["name"]!!.jsonPrimitive.content
        val genre = jsonObject["genre"]!!.jsonPrimitive.content
        val comments = jsonObject["comments"]?.jsonPrimitive?.content
        val trackNumber = jsonObject["trackNumber"]?.jsonPrimitive?.int?.toShort()
        val discNumber = jsonObject["discNumber"]?.jsonPrimitive?.int?.toShort()
        val bpm = jsonObject["bpm"]?.jsonPrimitive?.float
        val encoder = jsonObject["encoder"]?.jsonPrimitive?.content
        val encoding = jsonObject["encoding"]?.jsonPrimitive?.content
        val dateOfCreation = LocalDateTime.ofEpochSecond(jsonObject["dateOfCreation"]!!.jsonPrimitive.long, 0, ZoneOffset.UTC)
        val lastDateModified = LocalDateTime.ofEpochSecond(jsonObject["lastDateModified"]!!.jsonPrimitive.long, 0, ZoneOffset.UTC)

        return MutableAudioItem(
            path,
            id,
            title,
            duration,
            bitRate,
            ImmutableArtist.of(artistName, CountryCode.getByCode(artistCountryCode)),
            ImmutableAlbum(
                albumName,
                ImmutableArtist(albumArtistName),
                isCompilation,
                year.toShort(),
                ImmutableLabel(label)
            ),
            Genre.parseGenre(genre),
            comments,
            trackNumber,
            discNumber,
            bpm,
            encoder,
            encoding,
            dateOfCreation,
            lastDateModified
        )
    }

    override fun serialize(encoder: Encoder, value: MutableAudioItem) {
        val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException("This class can be saved only by Json")
        val jsonObject = buildJsonObject {
            put("id", value.id)
            put("path", value.path.absolutePathString())
            put("title", value.title)
            put("duration", value.duration.toSeconds())
            put("bitRate", value.bitRate)
            put("artist", buildJsonObject {
                put("name", value.artist.name)
                put("countryCode", value.artist.countryCode.name)
            })
            put("album", buildJsonObject {
                put("name", value.album.name)
                put("albumArtist", buildJsonObject {
                    put("name", value.album.albumArtist.name)
                })
                put("isCompilation", value.album.isCompilation)
                put("year", value.album.year)
                put("label", buildJsonObject {
                    put("name", value.album.label.name)
                })
            })
            put("genre", value.genre.name)
            value.comments?.let { put("comments", it) }
            value.trackNumber?.let { put("trackNumber", it) }
            value.discNumber?.let { put("discNumber", it) }
            value.bpm?.let { put("bpm", it) }
            value.encoder?.let { put("encoder", it) }
            value.encoding?.let { put("encoding", it) }
            put("dateOfCreation", value.dateOfCreation.toEpochSecond(ZoneOffset.UTC))
            put("lastDateModified", value.lastDateModified.toEpochSecond(ZoneOffset.UTC))
        }

        jsonOutput.encodeJsonElement(jsonObject)
    }
}