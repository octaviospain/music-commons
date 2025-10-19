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

import net.transgressoft.commons.persistence.json.TransEntityPolymorphicSerializer
import com.neovisionaries.i18n.CountryCode
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.io.path.Path
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

@get:JvmName("AudioItemMapSerializer")
val AudioItemMapSerializer: KSerializer<Map<Int, AudioItem>> = MapSerializer(Int.serializer(), AudioItemSerializer)

/**
 * Kotlinx serialization serializer for [AudioItem] instances.
 *
 * Handles JSON serialization and deserialization of audio items, preserving all metadata
 * fields including artist, album, and label information. Creates [MutableAudioItem]
 * instances during deserialization to enable metadata modification.
 */
internal object AudioItemSerializer: AudioItemSerializerBase<AudioItem>() {

    override fun createInstance(propertiesList: List<Any?>): AudioItem =
        MutableAudioItem(
            // path
            propertiesList[0] as Path,
            // id
            propertiesList[1] as Int,
            // title
            propertiesList[2] as String,
            // duration
            propertiesList[3] as Duration,
            // bitRate
            propertiesList[4] as Int,
            // artist name and artist country code
            ImmutableArtist.of(propertiesList[5] as String, CountryCode.getByCode(propertiesList[6] as String)),
            ImmutableAlbum(
                // album name
                propertiesList[7] as String,
                // album artist name
                ImmutableArtist.of(propertiesList[8] as String),
                // album isCompilation
                propertiesList[9] as Boolean,
                // album year
                propertiesList[10] as Short?,
                // album label name
                ImmutableLabel.of(propertiesList[11] as String)
            ),
            // genre
            Genre.parseGenre(propertiesList[12] as String),
            // comments
            propertiesList[13] as String?,
            // trackNumber
            propertiesList[14] as Short?,
            // discNumber
            propertiesList[15] as Short?,
            // bpm
            propertiesList[16] as Float?,
            // encoder
            propertiesList[17] as String?,
            // encoding
            propertiesList[18] as String?,
            // dateOfCreation
            propertiesList[19] as LocalDateTime,
            // lastDateModified
            propertiesList[20] as LocalDateTime,
            // playCount
            propertiesList[21] as Short
        )
}

abstract class AudioItemSerializerBase<I: ReactiveAudioItem<I>>: TransEntityPolymorphicSerializer<I> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("AudioItem") {
            element<String>("id")
            element<String>("path")
        }

    override fun getPropertiesList(decoder: Decoder): List<Any?> {
        val propertiesList = mutableListOf<Any?>()
        val jsonInput = decoder as? JsonDecoder ?: throw SerializationException("This class can be saved only by Json")
        val jsonObject = jsonInput.decodeJsonElement().jsonObject

        // non-nullable properties

        val path = jsonObject["path"] ?: throw SerializationException("Serialized AudioItem should contain path element")
        propertiesList.add(Path(path.jsonPrimitive.content))

        val id = jsonObject["id"] ?: throw SerializationException("Serialized AudioItem should contain id element")
        propertiesList.add(id.jsonPrimitive.int)

        val title = jsonObject["title"] ?: throw SerializationException("Serialized AudioItem should contain title element")
        propertiesList.add(title.jsonPrimitive.content)

        val duration = jsonObject["duration"] ?: throw SerializationException("Serialized AudioItem should contain duration element")
        propertiesList.add(Duration.ofSeconds(duration.jsonPrimitive.long))

        val bitRate = jsonObject["bitRate"] ?: throw SerializationException("Serialized AudioItem should contain bitRate element")
        propertiesList.add(bitRate.jsonPrimitive.int)

        val artist = jsonObject["artist"]?.jsonObject ?: throw SerializationException("Serialized AudioItem should contain artist element")
        val artistName = artist["name"] ?: throw SerializationException("Serialized AudioItem should contain artist.name element")
        propertiesList.add(artistName.jsonPrimitive.content)

        val countryCode = artist["countryCode"] ?: throw SerializationException("Serialized AudioItem should contain artist.countryCode element")
        propertiesList.add(countryCode.jsonPrimitive.content)

        val album = jsonObject["album"]?.jsonObject ?: throw SerializationException("Serialized AudioItem should contain album element")
        val albumName = album["name"] ?: throw SerializationException("Serialized AudioItem should contain album.name element")
        propertiesList.add(albumName.jsonPrimitive.content)

        val albumArtist = album["albumArtist"]?.jsonObject ?: throw SerializationException("Serialized AudioItem should contain album.albumArtist element")
        val albumArtistName = albumArtist["name"] ?: throw SerializationException("Serialized AudioItem should contain album.albumArtist.name element")
        propertiesList.add(albumArtistName.jsonPrimitive.content)

        val isCompilation = album["isCompilation"] ?: throw SerializationException("Serialized AudioItem should contain album.isCompilation element")
        propertiesList.add(isCompilation.jsonPrimitive.boolean)

        // year is nullable
        propertiesList.add(album["year"]?.jsonPrimitive?.intOrNull?.toShort())

        val label = album["label"]?.jsonObject ?: throw SerializationException("Serialized AudioItem should contain album.label element")
        val labelName = label["name"] ?: throw SerializationException("Serialized AudioItem should contain album.label.name element")
        propertiesList.add(labelName.jsonPrimitive.content)

        val genre = jsonObject["genre"] ?: throw SerializationException("Serialized AudioItem should contain genre element")
        propertiesList.add(genre.jsonPrimitive.content)

        // other nullable properties

        propertiesList.add(jsonObject["comments"]?.jsonPrimitive?.contentOrNull)
        propertiesList.add(jsonObject["trackNumber"]?.jsonPrimitive?.intOrNull?.toShort())
        propertiesList.add(jsonObject["discNumber"]?.jsonPrimitive?.intOrNull?.toShort())
        propertiesList.add(jsonObject["bpm"]?.jsonPrimitive?.floatOrNull)
        propertiesList.add(jsonObject["encoder"]?.jsonPrimitive?.contentOrNull)
        propertiesList.add(jsonObject["encoding"]?.jsonPrimitive?.contentOrNull)

        // end of other nullable properties

        // date properties are non-nullable

        val dateOfCreation = jsonObject["dateOfCreation"] ?: throw SerializationException("Serialized AudioItem should contain dateOfCreation element")
        propertiesList.add(LocalDateTime.ofEpochSecond(dateOfCreation.jsonPrimitive.long, 0, ZoneOffset.UTC))

        val lastDateModified = jsonObject["lastDateModified"] ?: throw SerializationException("Serialized AudioItem should contain lastDateModified element")
        propertiesList.add(LocalDateTime.ofEpochSecond(lastDateModified.jsonPrimitive.long, 0, ZoneOffset.UTC))

        // But playCount is nullable.
        // As you can figure out already, the order of properties is important in this list

        propertiesList.add(jsonObject["playCount"]?.jsonPrimitive?.int?.toShort())

        return propertiesList
    }

    override fun serialize(encoder: Encoder, value: I) {
        val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException("This class can be saved only by Json")
        jsonOutput.encodeJsonElement(value.toJsonObject())
    }
}