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

import net.transgressoft.lirp.persistence.json.LirpEntityPolymorphicSerializer
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

@get:JvmName("AudioItemMapSerializer")
internal val AudioItemMapSerializer: KSerializer<Map<Int, AudioItem>> = MapSerializer(Int.serializer(), AudioItemSerializer)

/**
 * Kotlinx serialization serializer for [AudioItem] instances.
 *
 * Handles JSON serialization and deserialization of audio items, preserving all metadata
 * fields including artist, album, and label information. Creates [MutableAudioItem]
 * instances during deserialization to enable metadata modification.
 */
internal object AudioItemSerializer : AudioItemSerializerBase<AudioItem>() {

    override fun constructEntity(
        path: Path,
        id: Int,
        title: String,
        duration: Duration,
        bitRate: Int,
        artist: Artist,
        album: Album,
        genre: Genre,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        encoding: String?,
        dateOfCreation: LocalDateTime,
        lastDateModified: LocalDateTime,
        playCount: Short
    ): AudioItem =
        MutableAudioItem(
            path, id, title, duration, bitRate, artist, album, genre,
            comments, trackNumber, discNumber, bpm, encoder, encoding,
            dateOfCreation, lastDateModified, playCount
        )
}

abstract class AudioItemSerializerBase<I : ReactiveAudioItem<I>> : LirpEntityPolymorphicSerializer<I> {

    // This serializer is JSON-only: deserialize() reads fields by name via JsonDecoder,
    // so the descriptor serves as a schema identity marker, not for positional decoding.
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("AudioItem") {
            element<String>("id")
            element<String>("path")
        }

    /**
     * Constructs a concrete audio item instance from deserialized properties.
     *
     * @param path audio file path
     * @param id entity identifier
     * @param title track title
     * @param duration track duration
     * @param bitRate audio bitrate in kbps
     * @param artist track artist
     * @param album track album
     * @param genre track genre
     * @param comments optional comments
     * @param trackNumber optional track number
     * @param discNumber optional disc number
     * @param bpm optional beats per minute
     * @param encoder optional encoder name
     * @param encoding optional encoding type
     * @param dateOfCreation creation timestamp
     * @param lastDateModified last modification timestamp
     * @param playCount number of times played
     */
    @SuppressWarnings("kotlin:S107")
    protected abstract fun constructEntity(
        path: Path,
        id: Int,
        title: String,
        duration: Duration,
        bitRate: Int,
        artist: Artist,
        album: Album,
        genre: Genre,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        encoding: String?,
        dateOfCreation: LocalDateTime,
        lastDateModified: LocalDateTime,
        playCount: Short
    ): I

    override fun deserialize(decoder: Decoder): I {
        val jsonInput = decoder as? JsonDecoder ?: throw SerializationException("This class can be saved only by Json")
        val json = jsonInput.decodeJsonElement().jsonObject

        fun require(key: String): JsonElement =
            json[key]?.takeUnless { it is JsonNull }
                ?: throw SerializationException("Missing required field '$key' in AudioItem JSON")

        fun requireString(obj: kotlinx.serialization.json.JsonObject, key: String, fullKey: String = key): String =
            obj[key]?.jsonPrimitive?.contentOrNull
                ?: throw SerializationException("Missing required field '$fullKey' in AudioItem JSON")

        val path = Path(requireString(json, "path"))
        val id = require("id").jsonPrimitive.int
        val title = requireString(json, "title")
        val duration = Duration.ofSeconds(require("duration").jsonPrimitive.long)
        val bitRate = require("bitRate").jsonPrimitive.int

        val artistObj = require("artist").jsonObject
        val artistName = requireString(artistObj, "name", "artist.name")
        val countryCodeStr = requireString(artistObj, "countryCode", "artist.countryCode")
        val artist = ImmutableArtist.of(artistName, CountryCode.getByCode(countryCodeStr) ?: CountryCode.UNDEFINED)

        val albumObj = require("album").jsonObject
        val albumName = requireString(albumObj, "name", "album.name")
        val albumArtistObj =
            albumObj["albumArtist"]?.jsonObject
                ?: throw SerializationException("Missing required field 'album.albumArtist' in AudioItem JSON")
        val albumArtistName = requireString(albumArtistObj, "name", "album.albumArtist.name")
        val albumArtistCountryCodeStr = albumArtistObj["countryCode"]?.jsonPrimitive?.contentOrNull
        val albumArtistCountryCode =
            albumArtistCountryCodeStr
                ?.let { CountryCode.getByCode(it) ?: CountryCode.UNDEFINED }
                ?: CountryCode.UNDEFINED
        val isCompilation =
            (
                albumObj["isCompilation"]
                    ?: throw SerializationException("Missing required field 'album.isCompilation' in AudioItem JSON")
            ).jsonPrimitive.boolean
        val year: Short? = albumObj["year"]?.jsonPrimitive?.intOrNull?.toShort()
        val labelObj =
            albumObj["label"]?.jsonObject
                ?: throw SerializationException("Missing required field 'album.label' in AudioItem JSON")
        val labelName = requireString(labelObj, "name", "album.label.name")
        val album = ImmutableAlbum(albumName, ImmutableArtist.of(albumArtistName, albumArtistCountryCode), isCompilation, year, ImmutableLabel.of(labelName))

        val genre = Genre.parseGenre(requireString(json, "genre"))
        val comments = json["comments"]?.jsonPrimitive?.contentOrNull
        val trackNumber = json["trackNumber"]?.jsonPrimitive?.intOrNull?.toShort()
        val discNumber = json["discNumber"]?.jsonPrimitive?.intOrNull?.toShort()
        val bpm = json["bpm"]?.jsonPrimitive?.floatOrNull
        val encoder = json["encoder"]?.jsonPrimitive?.contentOrNull
        val encoding = json["encoding"]?.jsonPrimitive?.contentOrNull
        val dateOfCreation = LocalDateTime.ofEpochSecond(require("dateOfCreation").jsonPrimitive.long, 0, ZoneOffset.UTC)
        val lastDateModified = LocalDateTime.ofEpochSecond(require("lastDateModified").jsonPrimitive.long, 0, ZoneOffset.UTC)
        val playCount = json["playCount"]?.jsonPrimitive?.intOrNull?.toShort() ?: 0.toShort()

        return constructEntity(
            path, id, title, duration, bitRate, artist, album, genre,
            comments, trackNumber, discNumber, bpm, encoder, encoding,
            dateOfCreation, lastDateModified, playCount
        )
    }

    override fun createInstance(propertiesList: List<Any?>): I =
        throw UnsupportedOperationException("Not used - deserialize overridden directly")

    override fun getPropertiesList(decoder: Decoder): List<Any?> =
        throw UnsupportedOperationException("Not used - deserialize overridden directly")

    override fun serialize(encoder: Encoder, value: I) {
        val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException("This class can be saved only by Json")
        jsonOutput.encodeJsonElement(value.toJsonObject())
    }
}