package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.serialization.json.Json

/**
 * Tests for [AudioWaveformSerializer] covering golden JSON fixture deserialization
 * and round-trip serialization fidelity.
 */
@DisplayName("AudioWaveformSerializer")
internal class AudioWaveformSerializerTest : StringSpec({

    val json = Json { prettyPrint = false }

    "AudioWaveformSerializer decodes golden JSON fixture with expected field values" {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()

        val goldenJson =
            """
            {
              "1": {
                "id": 1,
                "audioFilePath": "${realAudioPath.toAbsolutePath()}"
              }
            }
            """.trimIndent()

        val result = json.decodeFromString(AudioWaveformMapSerializer, goldenJson)

        result.size shouldBe 1
        result.containsKey(1) shouldBe true

        val waveform = result.getValue(1)

        waveform.id shouldBe 1
        waveform.audioFilePath shouldBe realAudioPath.toAbsolutePath()
    }

    "AudioWaveformSerializer round-trip serialization produces equal entity fields" {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val originalWaveform = ScalableAudioWaveform(42, realAudioPath)

        val encoded = json.encodeToString(AudioWaveformMapSerializer, mapOf(42 to originalWaveform as AudioWaveform))
        val decoded = json.decodeFromString(AudioWaveformMapSerializer, encoded)

        val decodedWaveform = decoded.getValue(42)

        decodedWaveform.id shouldBe 42
        decodedWaveform.audioFilePath shouldBe originalWaveform.audioFilePath
    }

    "AudioWaveformSerializer encodes id and audioFilePath" {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val waveform = ScalableAudioWaveform(7, realAudioPath)

        val encoded = json.encodeToString(AudioWaveformMapSerializer, mapOf(7 to waveform as AudioWaveform))

        encoded shouldContain "\"id\":7"
        encoded shouldContain realAudioPath.toAbsolutePath().toString()
    }
})