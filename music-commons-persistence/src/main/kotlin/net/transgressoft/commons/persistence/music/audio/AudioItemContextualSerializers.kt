/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.persistence.music.audio

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.Label
import net.transgressoft.commons.music.audio.parseGenre
import net.transgressoft.commons.util.toJsonUri
import net.transgressoft.commons.util.toPathFromJsonUri
import com.neovisionaries.i18n.CountryCode
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.SerializersModule

/**
 * Contextual [KSerializer]s for the audio-item value types that the reactive base modules keep
 * annotation-free, plus the [audioItemSerializersModule] that registers them.
 *
 * The base value types ([Artist], [Album], [Label], [Genre], [AudioItemMetadata]) and the JDK
 * types they nest ([Path], [Duration], [LocalDateTime], [CountryCode]) are deliberately not
 * `@Serializable`: serialization knowledge lives at this persistence boundary, not in the domain.
 * lirp's reflective entity serializer resolves a serializer for every nested field through the
 * [SerializersModule] passed to `lirpSerializer(sample, module)`, so registering these as
 * contextual serializers lets the audio item round-trip without annotating the domain.
 *
 * The per-type JSON shape mirrors the prior hand-rolled audio-item wire format so existing
 * libraries stay readable: artist/label as `{name, countryCode}`, album as a nested object, genres
 * as their display-name strings, and paths as `file://` URIs.
 */
object PathContextualSerializer : KSerializer<Path> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.toJsonUri())

    override fun deserialize(decoder: Decoder): Path = decoder.decodeString().toPathFromJsonUri()
}

object DurationContextualSerializer : KSerializer<Duration> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Duration", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeLong(value.toSeconds())

    override fun deserialize(decoder: Decoder): Duration = Duration.ofSeconds(decoder.decodeLong())
}

/**
 * Encodes a [LocalDateTime] as a UTC epoch-second [Long] for a compact wire form.
 *
 * Sub-second precision is intentionally dropped: timestamps round-trip truncated to whole seconds.
 */
object LocalDateTimeContextualSerializer : KSerializer<LocalDateTime> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeLong(value.toEpochSecond(ZoneOffset.UTC))

    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.ofEpochSecond(decoder.decodeLong(), 0, ZoneOffset.UTC)
}

object CountryCodeContextualSerializer : KSerializer<CountryCode> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CountryCode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CountryCode) = encoder.encodeString(value.name)

    // The enum constant name (e.g. "US", "UNDEFINED") round-trips through valueOf, which also
    // resolves the UNDEFINED sentinel that getByCode rejects.
    override fun deserialize(decoder: Decoder): CountryCode = CountryCode.valueOf(decoder.decodeString())
}

/**
 * Encodes a [Genre] as its display name.
 *
 * On read the name is resolved through [parseGenre], so a name matching a standard genre
 * (case-insensitively) is canonicalized to that standard subtype and any other name is preserved as
 * [Genre.Custom] — identical to [GenreConverter]'s SQL behavior, keeping the two persistence paths
 * consistent with metadata parsing.
 */
object GenreContextualSerializer : KSerializer<Genre> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Genre", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Genre) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): Genre {
        val raw = decoder.decodeString()
        return parseGenre(raw).firstOrNull() ?: Genre.Custom(raw)
    }
}

object ArtistContextualSerializer : KSerializer<Artist> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Artist") {
            element<String>("name")
            element<String>("countryCode")
        }

    override fun serialize(encoder: Encoder, value: Artist) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Artist can be serialized only with Json")
        jsonEncoder.encodeJsonElement(value.toJsonObject())
    }

    override fun deserialize(decoder: Decoder): Artist {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Artist can be deserialized only with Json")
        return jsonDecoder.decodeJsonElement().jsonObject.toArtist()
    }
}

object LabelContextualSerializer : KSerializer<Label> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Label") {
            element<String>("name")
            element<String>("countryCode")
        }

    override fun serialize(encoder: Encoder, value: Label) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Label can be serialized only with Json")
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("name", value.name)
                put("countryCode", value.countryCode.name)
            }
        )
    }

    override fun deserialize(decoder: Decoder): Label {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Label can be deserialized only with Json")
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        return Label.of(
            obj.requireString("name", "label.name"),
            obj.countryCode()
        )
    }
}

object AlbumContextualSerializer : KSerializer<Album> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Album") {
            element<String>("name")
            element<JsonObject>("albumArtist")
            element<Boolean>("isCompilation")
            element<Short?>("year")
            element<JsonObject>("label")
        }

    override fun serialize(encoder: Encoder, value: Album) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Album can be serialized only with Json")
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("name", value.name)
                put("albumArtist", value.albumArtist.toJsonObject())
                put("isCompilation", value.isCompilation)
                put("year", value.year)
                put(
                    "label",
                    buildJsonObject {
                        put("name", value.label.name)
                        put("countryCode", value.label.countryCode.name)
                    }
                )
            }
        )
    }

    override fun deserialize(decoder: Decoder): Album {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Album can be deserialized only with Json")
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val albumArtistObj =
            obj["albumArtist"]?.jsonObject ?: throw SerializationException("Missing required field 'album.albumArtist'")
        val labelObj = obj["label"]?.jsonObject ?: throw SerializationException("Missing required field 'album.label'")
        return Album(
            name = obj.requireString("name", "album.name"),
            albumArtist = albumArtistObj.toArtist(),
            isCompilation = (obj["isCompilation"] ?: throw SerializationException("Missing required field 'album.isCompilation'")).jsonPrimitive.boolean,
            year = obj["year"]?.jsonPrimitive?.intOrNull?.toShort(),
            label = Label.of(labelObj.requireString("name", "album.label.name"), labelObj.countryCode())
        )
    }
}

object AudioItemMetadataContextualSerializer : KSerializer<AudioItemMetadata> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("AudioItemMetadata") {
            element<String>("title")
            element<JsonObject>("artist")
            element<JsonObject>("album")
            element<List<String>>("genres")
            element<String?>("comments")
            element<Short?>("trackNumber")
            element<Short?>("discNumber")
            element<Float?>("bpm")
            element<String?>("encoder")
            element<String?>("encoding")
            element<Int>("bitRate")
            element<Long>("duration")
        }

    override fun serialize(encoder: Encoder, value: AudioItemMetadata) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("AudioItemMetadata can be serialized only with Json")
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("title", value.title)
                put("artist", value.artist.toJsonObject())
                put("album", jsonEncoder.json.encodeToJsonElement(AlbumContextualSerializer, value.album))
                put("genres", JsonArrayOfStrings(value.genres.map { it.name }.sorted()))
                put("comments", value.comments)
                put("trackNumber", value.trackNumber)
                put("discNumber", value.discNumber)
                put("bpm", value.bpm)
                put("encoder", value.encoder)
                put("encoding", value.encoding)
                put("bitRate", value.bitRate)
                put("duration", value.duration.toSeconds())
                // coverBytes travel out-of-band and are intentionally not serialized.
            }
        )
    }

    override fun deserialize(decoder: Decoder): AudioItemMetadata {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("AudioItemMetadata can be deserialized only with Json")
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val artistObj = obj["artist"]?.jsonObject ?: throw SerializationException("Missing required field 'metadata.artist'")
        val albumElement = obj["album"] ?: throw SerializationException("Missing required field 'metadata.album'")
        val genres: Set<Genre> =
            obj["genres"]?.let { element ->
                (element as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.flatMap { parseGenre(it) }
                    ?.toSet()
            } ?: emptySet()
        return AudioItemMetadata(
            title = obj.requireString("title", "metadata.title"),
            artist = artistObj.toArtist(),
            album = jsonDecoder.json.decodeFromJsonElement(AlbumContextualSerializer, albumElement),
            genres = genres,
            comments = obj["comments"]?.jsonPrimitive?.contentOrNull,
            trackNumber = obj["trackNumber"]?.jsonPrimitive?.intOrNull?.toShort(),
            discNumber = obj["discNumber"]?.jsonPrimitive?.intOrNull?.toShort(),
            bpm = obj["bpm"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull(),
            encoder = obj["encoder"]?.jsonPrimitive?.contentOrNull,
            encoding = obj["encoding"]?.jsonPrimitive?.contentOrNull,
            bitRate = obj["bitRate"]?.jsonPrimitive?.intOrNull ?: 0,
            duration = Duration.ofSeconds(obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L),
            coverBytes = null
        )
    }
}

/**
 * Contextual [SerializersModule] for the audio-item value types, passed to
 * `lirpSerializer(sample, module)` so the reflective entity serializer resolves serializers for
 * the non-`@Serializable` nested types. Reused by the FX-tier persistence module.
 */
@get:JvmName("audioItemSerializersModule")
val audioItemSerializersModule: SerializersModule =
    SerializersModule {
        contextual(Path::class, PathContextualSerializer)
        contextual(Duration::class, DurationContextualSerializer)
        contextual(LocalDateTime::class, LocalDateTimeContextualSerializer)
        contextual(CountryCode::class, CountryCodeContextualSerializer)
        contextual(Genre::class, GenreContextualSerializer)
        contextual(Artist::class, ArtistContextualSerializer)
        contextual(Label::class, LabelContextualSerializer)
        contextual(Album::class, AlbumContextualSerializer)
        contextual(AudioItemMetadata::class, AudioItemMetadataContextualSerializer)
    }

private fun Artist.toJsonObject(): JsonObject =
    buildJsonObject {
        put("name", name)
        put("countryCode", countryCode.name)
    }

private fun JsonObject.toArtist(): Artist =
    Artist.of(requireString("name", "artist.name"), countryCode())

private fun JsonObject.countryCode(): CountryCode =
    this["countryCode"]?.jsonPrimitive?.contentOrNull?.let { CountryCode.getByCode(it) ?: runCatching { CountryCode.valueOf(it) }.getOrNull() }
        ?: CountryCode.UNDEFINED

private fun JsonObject.requireString(key: String, fullKey: String = key): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: throw SerializationException("Missing required field '$fullKey'")

@Suppress("FunctionName")
private fun JsonArrayOfStrings(values: List<String>) = kotlinx.serialization.json.JsonArray(values.map { JsonPrimitive(it) })