package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Tests for [AudioWaveformSerializer] covering golden JSON fixture deserialization,
 * round-trip serialization fidelity, and Base64 cache field encoding.
 */
@DisplayName("AudioWaveformSerializer")
internal class AudioWaveformSerializerTest : StringSpec({

    val json = Json { prettyPrint = false }

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

    "AudioWaveformSerializer encodes cachedWidth and normalizedAmplitudes in JSON when cache is populated" {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val waveform = ScalableAudioWaveform(5, realAudioPath)
        runBlocking { waveform.amplitudes(780, 335) }

        val encoded = json.encodeToString(AudioWaveformMapSerializer, mapOf(5 to waveform as AudioWaveform))

        encoded shouldContain "\"cachedWidth\":780"
        encoded shouldContain "\"normalizedAmplitudes\":"
    }

    "AudioWaveformSerializer round-trip with cached amplitudes preserves cachedWidth and normalizedAmplitudes size" {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val waveform = ScalableAudioWaveform(10, realAudioPath)
        runBlocking { waveform.amplitudes(780, 335) }

        val encoded = json.encodeToString(AudioWaveformMapSerializer, mapOf(10 to waveform as AudioWaveform))
        val decoded = json.decodeFromString(AudioWaveformMapSerializer, encoded)

        val decodedWaveform = decoded.getValue(10) as ScalableAudioWaveform

        decodedWaveform.cachedWidth shouldBe 780
        decodedWaveform.normalizedAmplitudes.shouldNotBeNull()
        decodedWaveform.normalizedAmplitudes!!.size shouldBe 780
    }

    "AudioWaveformSerializer decodes JSON with cachedWidth and normalizedAmplitudes fields" {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val originalAmplitudes = FloatArray(780) { it * 0.001f }
        val base64Amplitudes = originalAmplitudes.toBase64String()

        val goldenJson =
            """
            {
              "3": {
                "id": 3,
                "audioFilePath": "${realAudioPath.toAbsolutePath()}",
                "cachedWidth": 780,
                "normalizedAmplitudes": "$base64Amplitudes"
              }
            }
            """.trimIndent()

        val result = json.decodeFromString(AudioWaveformMapSerializer, goldenJson)
        val waveform = result.getValue(3) as ScalableAudioWaveform

        waveform.cachedWidth shouldBe 780
        waveform.normalizedAmplitudes.shouldNotBeNull()
        waveform.normalizedAmplitudes!!.size shouldBe 780
    }

    "FloatArray Base64 round-trip preserves all float values exactly" {
        val original = FloatArray(100) { it * 0.01f }
        val encoded = original.toBase64String()
        val decoded = encoded.toFloatArrayFromBase64()

        decoded.contentEquals(original).shouldBeTrue()
    }
})