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

import java.nio.file.Path
import java.nio.file.Paths
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
 * Serializes waveforms by storing the audio file path and ID, allowing waveforms to be
 * reconstructed on deserialization by re-reading the audio file.
 */
object AudioWaveformSerializer : KSerializer<AudioWaveform> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("AudioWaveform") {
            element<String>("id")
            element<Path>("audioFilePath")
        }

    override fun deserialize(decoder: Decoder): AudioWaveform {
        val jsonInput = decoder as? JsonDecoder ?: throw SerializationException("This class can be saved only by Json")
        val jsonObject = jsonInput.decodeJsonElement().jsonObject
        val id = jsonObject["id"]!!.jsonPrimitive.int
        val audioFilePathString = jsonObject["audioFilePath"]!!.jsonPrimitive.content
        return ScalableAudioWaveform(id, Paths.get(audioFilePathString))
    }

    override fun serialize(encoder: Encoder, value: AudioWaveform) {
        val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException("This class can be saved only by Json")
        val jsonObject =
            buildJsonObject {
                put("id", value.id)
                put("audioFilePath", value.audioFilePath.absolutePathString())
            }
        jsonOutput.encodeJsonElement(jsonObject)
    }
}