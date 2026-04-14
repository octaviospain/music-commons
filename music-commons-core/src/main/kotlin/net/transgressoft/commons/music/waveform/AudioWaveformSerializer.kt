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

package net.transgressoft.commons.music.waveform

import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import kotlin.io.path.absolutePathString
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@get:JvmName("AudioWaveformMapSerializer")
val AudioWaveformMapSerializer: KSerializer<Map<Int, AudioWaveform>> = MapSerializer(Int.serializer(), AudioWaveformSerializer)

/**
 * Kotlinx serialization serializer for [AudioWaveform] instances.
 *
 * Serializes waveforms by storing the audio file path, ID, cached display width, and
 * Base64-encoded normalized amplitudes. On deserialization, the cached values are restored
 * so that same-width requests are served without re-reading the audio file.
 *
 * All four fields (`id`, `audioFilePath`, `cachedWidth`, `normalizedAmplitudes`) are required.
 * Malformed Base64 payloads and cache size mismatches produce [kotlinx.serialization.SerializationException].
 */
object AudioWaveformSerializer : KSerializer<AudioWaveform> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("AudioWaveform") {
            element<String>("id")
            element<Path>("audioFilePath")
            element<Int>("cachedWidth")
            element<String>("normalizedAmplitudes")
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

        return ScalableAudioWaveform(
            id,
            Paths.get(audioFilePathString),
            cachedWidth,
            decodedAmplitudes
        )
    }

    override fun serialize(encoder: Encoder, value: AudioWaveform) {
        val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException("This class can be saved only by Json")
        val sw = value as ScalableAudioWaveform
        val snapshot = sw.normalizedAmplitudesSnapshot ?: FloatArray(0)
        val jsonObject =
            buildJsonObject {
                put("id", value.id)
                put("audioFilePath", value.audioFilePath.absolutePathString())
                put("cachedWidth", sw.cachedWidth)
                put("normalizedAmplitudes", snapshot.toBase64String())
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