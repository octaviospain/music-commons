package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.json.TransEntityPolymorphicSerializer
import com.neovisionaries.i18n.CountryCode
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

internal object AudioItemSerializer : AudioItemSerializerBase<AudioItem>() {

    override fun createInstance(propertiesList: List<Any?>): AudioItem =
        MutableAudioItem(
            propertiesList[0] as Path,
            propertiesList[1] as Int,
            propertiesList[2] as String,
            propertiesList[3] as Duration,
            propertiesList[4] as Int,
            ImmutableArtist.of(propertiesList[5] as String, CountryCode.getByCode(propertiesList[6] as String)),
            ImmutableAlbum(
                propertiesList[7] as String,
                ImmutableArtist(propertiesList[8] as String),
                propertiesList[9] as Boolean,
                propertiesList[10] as Short,
                ImmutableLabel(propertiesList[11] as String)
            ),
            Genre.parseGenre(propertiesList[12] as String),
            propertiesList[13] as String?,
            propertiesList[14] as Short,
            propertiesList[15] as Short,
            propertiesList[16] as Float,
            propertiesList[17] as String?,
            propertiesList[18] as String?,
            propertiesList[19] as LocalDateTime,
            propertiesList[20] as LocalDateTime, propertiesList[21] as Short
        )
}

abstract class AudioItemSerializerBase<I : ReactiveAudioItem<I>> : TransEntityPolymorphicSerializer<I> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("AudioItem") {
            element<String>("id")
            element<String>("path")
        }

    override fun getPropertiesList(decoder: Decoder): List<Any?> {
        val propertiesList = mutableListOf<Any?>()
        val jsonInput = decoder as? JsonDecoder ?: throw SerializationException("This class can be saved only by Json")
        val jsonObject = jsonInput.decodeJsonElement().jsonObject

        propertiesList.add(Path(jsonObject["path"]!!.jsonPrimitive.content))
        propertiesList.add(jsonObject["id"]!!.jsonPrimitive.int)
        propertiesList.add(jsonObject["title"]!!.jsonPrimitive.content)
        propertiesList.add(Duration.ofSeconds(jsonObject["duration"]!!.jsonPrimitive.long))
        propertiesList.add(jsonObject["bitRate"]!!.jsonPrimitive.int)
        propertiesList.add(jsonObject["artist"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        propertiesList.add(jsonObject["artist"]!!.jsonObject["countryCode"]!!.jsonPrimitive.content)
        propertiesList.add(jsonObject["album"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        propertiesList.add(jsonObject["album"]!!.jsonObject["albumArtist"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        propertiesList.add(jsonObject["album"]!!.jsonObject["isCompilation"]!!.jsonPrimitive.boolean)
        propertiesList.add(jsonObject["album"]!!.jsonObject["year"]!!.jsonPrimitive.int.toShort())
        propertiesList.add(jsonObject["album"]!!.jsonObject["label"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        propertiesList.add(jsonObject["genre"]!!.jsonPrimitive.content)
        propertiesList.add(jsonObject["comments"]?.jsonPrimitive?.content)
        propertiesList.add(jsonObject["trackNumber"]?.jsonPrimitive?.int?.toShort())
        propertiesList.add(jsonObject["discNumber"]?.jsonPrimitive?.int?.toShort())
        propertiesList.add(jsonObject["bpm"]?.jsonPrimitive?.float)
        propertiesList.add(jsonObject["encoder"]?.jsonPrimitive?.content)
        propertiesList.add(jsonObject["encoding"]?.jsonPrimitive?.content)
        propertiesList.add(LocalDateTime.ofEpochSecond(jsonObject["dateOfCreation"]!!.jsonPrimitive.long, 0, ZoneOffset.UTC))
        propertiesList.add(LocalDateTime.ofEpochSecond(jsonObject["lastDateModified"]!!.jsonPrimitive.long, 0, ZoneOffset.UTC))
        propertiesList.add(jsonObject["playCount"]?.jsonPrimitive?.int?.toShort())

        return propertiesList
    }

    override fun serialize(encoder: Encoder, value: I) {
        val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException("This class can be saved only by Json")
        val jsonObject =
            buildJsonObject {
                put("id", value.id)
                put("path", value.path.absolutePathString())
                put("title", value.title)
                put("duration", value.duration.toSeconds())
                put("bitRate", value.bitRate)
                put(
                    "artist",
                    buildJsonObject {
                        put("name", value.artist.name)
                        put("countryCode", value.artist.countryCode.name)
                    }
                )
                put(
                    "album",
                    buildJsonObject {
                        put("name", value.album.name)
                        put(
                            "albumArtist",
                            buildJsonObject {
                                put("name", value.album.albumArtist.name)
                            }
                        )
                        put("isCompilation", value.album.isCompilation)
                        put("year", value.album.year)
                        put(
                            "label",
                            buildJsonObject {
                                put("name", value.album.label.name)
                            }
                        )
                    }
                )
                put("genre", value.genre.name)
                value.comments?.let { put("comments", it) }
                value.trackNumber?.let { put("trackNumber", it) }
                value.discNumber?.let { put("discNumber", it) }
                value.bpm?.let { put("bpm", it) }
                value.encoder?.let { put("encoder", it) }
                value.encoding?.let { put("encoding", it) }
                put("dateOfCreation", value.dateOfCreation.toEpochSecond(ZoneOffset.UTC))
                put("lastDateModified", value.lastDateModified.toEpochSecond(ZoneOffset.UTC))
                put("playCount", value.playCount)
            }

        jsonOutput.encodeJsonElement(jsonObject)
    }
}