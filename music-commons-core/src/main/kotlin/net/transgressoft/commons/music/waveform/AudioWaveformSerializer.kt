package net.transgressoft.commons.music.waveform

import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
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
                put("audioFilePath", value.audioFilePath.toAbsolutePath().toString())
            }
        jsonOutput.encodeJsonElement(jsonObject)
    }
}