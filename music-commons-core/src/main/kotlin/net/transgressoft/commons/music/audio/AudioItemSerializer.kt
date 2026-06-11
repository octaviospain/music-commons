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

import net.transgressoft.commons.util.toPathFromJsonUri
import net.transgressoft.lirp.persistence.json.LirpEntityPolymorphicSerializer
import com.neovisionaries.i18n.CountryCode
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * [KSerializer] for `Map<Int, AudioItem>` used to round-trip an audio library through JSON.
 *
 * Consumers wiring a custom `JsonFileRepository` pass this serializer directly:
 *
 * ```
 * val repository = JsonFileRepository(audioFile, AudioItemMapSerializer)
 * MusicLibrary.builder().audioRepository(repository).build()
 * ```
 *
 * The underlying element serializer is the polymorphic [AudioItemSerializer], which materializes
 * deserialized entries as [MutableAudioItem] instances. Polymorphic subtypes for [Artist], [Album]
 * and [Label] are constructed directly; they are no longer registered as polymorphic subtypes via
 * [audioItemSerializerModule]; when building a `Json` instance manually, pass that module as
 * `serializersModule` so subtypes resolve correctly. `JsonFileRepository` wires it automatically.
 *
 * Thread-safety: the serializer is stateless; concurrent reads are safe.
 *
 * @see audioItemSerializerModule
 */
@get:JvmName("AudioItemMapSerializer")
val AudioItemMapSerializer: KSerializer<Map<Int, AudioItem>> = MapSerializer(Int.serializer(), AudioItemSerializer())

/**
 * Kotlinx serialization serializer for [AudioItem] instances.
 *
 * Handles JSON serialization and deserialization of audio items, preserving all metadata
 * fields including artist, album, and label information. Creates [MutableAudioItem]
 * instances during deserialization to enable metadata modification.
 *
 * @param fileSystem the [FileSystem] used to materialize [Path] instances during
 *  deserialization. Defaults to [FileSystems.getDefault]; tests may pass a Jimfs
 *  filesystem to deserialize against an in-memory tree.
 */
internal class AudioItemSerializer
    @JvmOverloads
    constructor(
        fileSystem: FileSystem = FileSystems.getDefault()
    ) : AudioItemSerializerBase<AudioItem>(fileSystem) {

        override fun constructEntity(
            path: Path,
            id: Int,
            metadata: AudioItemMetadata,
            dateOfCreation: LocalDateTime,
            lastDateModified: LocalDateTime,
            playCount: Short
        ): AudioItem = MutableAudioItem(path, id, metadata, dateOfCreation, lastDateModified, playCount)
    }

abstract class AudioItemSerializerBase<I : ReactiveAudioItem<I>>(
    private val fileSystem: FileSystem = FileSystems.getDefault()
) : LirpEntityPolymorphicSerializer<I> {

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
     * @param metadata tag- and header-derived metadata (cover bytes are not on the JSON wire and remain null here)
     * @param dateOfCreation creation timestamp
     * @param lastDateModified last modification timestamp
     * @param playCount number of times played
     */
    protected abstract fun constructEntity(
        path: Path,
        id: Int,
        metadata: AudioItemMetadata,
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

        val pathString = requireString(json, "path")
        val path =
            if (fileSystem == FileSystems.getDefault()) {
                pathString.toPathFromJsonUri()
            } else {
                pathString.toPathFromJsonUri(fileSystem)
            }
        val id = require("id").jsonPrimitive.int
        val title = requireString(json, "title")
        val duration = Duration.ofSeconds(require("duration").jsonPrimitive.long)
        val bitRate = require("bitRate").jsonPrimitive.int

        val artistObj = require("artist").jsonObject
        val artistName = requireString(artistObj, "name", "artist.name")
        val countryCodeStr = requireString(artistObj, "countryCode", "artist.countryCode")
        val artist = Artist.of(artistName, CountryCode.getByCode(countryCodeStr) ?: CountryCode.UNDEFINED)

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
        val labelCountryCodeStr = labelObj["countryCode"]?.jsonPrimitive?.contentOrNull
        val labelCountryCode = labelCountryCodeStr?.let { CountryCode.getByCode(it) ?: CountryCode.UNDEFINED } ?: CountryCode.UNDEFINED
        val album =
            Album(
                albumName,
                Artist.of(albumArtistName, albumArtistCountryCode),
                isCompilation,
                year,
                Label.of(labelName, labelCountryCode)
            )

        val genres: Set<Genre> =
            json["genres"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.flatMap { parseGenre(it) }
                ?.toSet()
                ?: json["genre"]?.jsonPrimitive?.contentOrNull?.let { parseGenre(it) }
                ?: emptySet()
        val comments = json["comments"]?.jsonPrimitive?.contentOrNull
        val trackNumber = json["trackNumber"]?.jsonPrimitive?.intOrNull?.toShort()
        val discNumber = json["discNumber"]?.jsonPrimitive?.intOrNull?.toShort()
        val bpm = json["bpm"]?.jsonPrimitive?.floatOrNull
        val encoder = json["encoder"]?.jsonPrimitive?.contentOrNull
        val encoding = json["encoding"]?.jsonPrimitive?.contentOrNull
        val dateOfCreation = LocalDateTime.ofEpochSecond(require("dateOfCreation").jsonPrimitive.long, 0, ZoneOffset.UTC)
        val lastDateModified = LocalDateTime.ofEpochSecond(require("lastDateModified").jsonPrimitive.long, 0, ZoneOffset.UTC)
        val playCount = json["playCount"]?.jsonPrimitive?.intOrNull?.toShort() ?: 0.toShort()

        val metadata =
            AudioItemMetadata(
                title = title,
                artist = artist,
                album = album,
                genres = genres,
                comments = comments,
                trackNumber = trackNumber,
                discNumber = discNumber,
                bpm = bpm,
                encoder = encoder,
                encoding = encoding,
                bitRate = bitRate,
                duration = duration,
                coverBytes = null
            )
        return constructEntity(path, id, metadata, dateOfCreation, lastDateModified, playCount)
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