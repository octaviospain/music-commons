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

package net.transgressoft.commons.media.persistence.waveform

import net.transgressoft.commons.media.waveform.ScalableAudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.util.toJsonUri
import net.transgressoft.commons.util.toPathFromJsonUri
import java.nio.ByteBuffer
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * [KSerializer] for `Map<Int, AudioWaveform>` used to round-trip a waveform repository through JSON.
 *
 * Consumers wiring a custom `JsonFileRepository` pass this serializer directly:
 *
 * ```
 * val repository = JsonFileRepository(waveformsFile, AudioWaveformMapSerializer)
 * MusicLibrary.builder().waveformRepository(repository).build()
 * ```
 *
 * The element serializer is [AudioWaveformSerializer], which encodes the waveform's id,
 * audio-file path, cached display width, and Base64-packed normalized amplitudes. No polymorphic
 * `serializersModule` is required — waveforms are not polymorphic at the JSON level.
 *
 * Thread-safety: the serializer is stateless; concurrent reads are safe.
 * @since 1.0
 */
@get:JvmName("AudioWaveformMapSerializer")
public val AudioWaveformMapSerializer: KSerializer<Map<Int, AudioWaveform>> = MapSerializer(Int.serializer(), AudioWaveformSerializer())

/**
 * Kotlinx serialization serializer for [AudioWaveform] instances.
 *
 * Serializes waveforms by storing the audio file path, ID, cached display width, and
 * Base64-encoded normalized amplitudes. On deserialization, the cached values are restored
 * so that same-width requests are served without re-reading the audio file.
 *
 * The serializer is co-located with [ScalableAudioWaveform] in `music-commons-media`, so it reads the
 * cached display width and amplitudes snapshot through the entity's `internal` getters and rebuilds
 * the cache-bearing instance through the entity's `internal` deserialization constructor directly —
 * no reflection and no public factory.
 *
 * The four cache fields (`id`, `audioFilePath`, `cachedWidth`, `normalizedAmplitudes`) are required;
 * `lastDateModified` is optional and absent in waveforms persisted before it was round-tripped.
 * Malformed Base64 payloads and cache size mismatches produce [kotlinx.serialization.SerializationException].
 *
 * **Schema-change convention:** `AudioWaveformSerializer` is hand-written and independent of
 * `lirpSerializerFor` because [ScalableAudioWaveform] holds non-`@Serializable` cached fields
 * (`cachedWidth`, `normalizedAmplitudes`) that require manual encoding. Any new persisted field
 * MUST ship with a round-trip test covering both the field-absent case (older persisted data) and
 * the field-present case (new data), using [assertOptionalFieldRoundTrips][net.transgressoft.commons.music.testing.assertOptionalFieldRoundTrips]
 * from `music-commons-test`. The `lastDateModified` field is the reference example of the
 * optional-field pattern.
 *
 * @param fileSystem the [FileSystem] used to materialize the [java.nio.file.Path] backing
 *  the deserialized waveform. Defaults to [FileSystems.getDefault]; tests may pass a
 *  Jimfs filesystem to round-trip waveform JSON against an in-memory tree.
 * @since 1.0
 */
public class AudioWaveformSerializer
    @JvmOverloads
    constructor(
        private val fileSystem: FileSystem = FileSystems.getDefault()
    ) : KSerializer<AudioWaveform> {

        override val descriptor: SerialDescriptor =
            buildClassSerialDescriptor("AudioWaveform") {
                element<Int>("id")
                element<String>("audioFilePath")
                element<Int>("cachedWidth")
                element<String>("normalizedAmplitudes")
                element<Long>("lastDateModified", isOptional = true)
            }

        override fun deserialize(decoder: Decoder): AudioWaveform {
            val jsonInput = decoder as? JsonDecoder ?: throw SerializationException("This class can be saved only by Json")
            val jsonObject = jsonInput.decodeJsonElement().jsonObject
            val id =
                (jsonObject["id"] ?: throw SerializationException("Missing required field 'id' in AudioWaveform JSON"))
                    .jsonPrimitive.int
            val audioFilePathString =
                (jsonObject["audioFilePath"] ?: throw SerializationException("Missing required field 'audioFilePath' in AudioWaveform JSON"))
                    .jsonPrimitive.content
            val cachedWidth =
                (jsonObject["cachedWidth"] ?: throw SerializationException("Missing required field 'cachedWidth' in AudioWaveform JSON"))
                    .jsonPrimitive.int
            val normalizedAmplitudesBase64 =
                (jsonObject["normalizedAmplitudes"] ?: throw SerializationException("Missing required field 'normalizedAmplitudes' in AudioWaveform JSON"))
                    .jsonPrimitive.content

            val decodedAmplitudes =
                try {
                    normalizedAmplitudesBase64.toFloatArrayFromBase64()
                } catch (e: IllegalArgumentException) {
                    throw SerializationException("Malformed Base64 in normalizedAmplitudes for AudioWaveform id=$id", e)
                }

            if (decodedAmplitudes.size != cachedWidth) {
                throw SerializationException(
                    "Cache mismatch for AudioWaveform id=$id: cachedWidth=$cachedWidth but decoded ${decodedAmplitudes.size} amplitudes"
                )
            }

            val audioFilePath =
                if (fileSystem == FileSystems.getDefault()) {
                    audioFilePathString.toPathFromJsonUri()
                } else {
                    audioFilePathString.toPathFromJsonUri(fileSystem)
                }

            // Optional: absent in waveforms persisted before timestamp round-tripping was added.
            // A present-but-malformed value is corruption, not a legacy file, so it fails loudly.
            val lastDateModified =
                jsonObject["lastDateModified"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.let { raw ->
                        raw.toLongOrNull()
                            ?: throw SerializationException("Malformed lastDateModified for AudioWaveform id=$id")
                    }
                    ?.let { epochSecond ->
                        try {
                            LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC)
                        } catch (e: RuntimeException) {
                            throw SerializationException("Invalid lastDateModified for AudioWaveform id=$id", e)
                        }
                    }

            return ScalableAudioWaveform(id, audioFilePath, cachedWidth, decodedAmplitudes).also { waveform ->
                if (lastDateModified != null) {
                    waveform.lastDateModified = lastDateModified
                }
            }
        }

        override fun serialize(encoder: Encoder, value: AudioWaveform) {
            val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException("This class can be saved only by Json")
            val sw = value as ScalableAudioWaveform
            val snapshot = sw.normalizedAmplitudesSnapshot ?: FloatArray(0)
            val jsonObject =
                buildJsonObject {
                    put("id", value.id)
                    put("audioFilePath", value.audioFilePath.toJsonUri())
                    put("cachedWidth", sw.cachedWidth)
                    put("normalizedAmplitudes", snapshot.toBase64String())
                    put("lastDateModified", sw.lastDateModified.toEpochSecond(ZoneOffset.UTC))
                }
            jsonOutput.encodeJsonElement(jsonObject)
        }
    }

/**
 * Encodes this [FloatArray] to a Base64 string using IEEE 754 big-endian byte representation.
 * Each float occupies 4 bytes; the resulting byte array is Base64-encoded without line breaks.
 */
internal fun FloatArray.toBase64String(): String {
    val buffer = ByteBuffer.allocate(size * 4)
    forEach { buffer.putFloat(it) }
    return Base64.getEncoder().encodeToString(buffer.array())
}

/**
 * Decodes a Base64 string produced by [toBase64String] back to a [FloatArray].
 * The byte array length must be a multiple of 4.
 */
internal fun String.toFloatArrayFromBase64(): FloatArray {
    val bytes = Base64.getDecoder().decode(this)
    val buffer = ByteBuffer.wrap(bytes)
    return FloatArray(bytes.size / 4) { buffer.getFloat() }
}