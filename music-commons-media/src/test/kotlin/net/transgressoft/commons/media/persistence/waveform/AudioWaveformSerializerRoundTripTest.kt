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
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.util.toJsonUri
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.serialization.json.Json

/**
 * Round-trip coverage for [AudioWaveformMapSerializer], proving that waveform JSON persistence
 * preserves audio-file path, cached display width, and the Base64-packed normalized amplitudes
 * through a serialize → deserialize → re-serialize cycle.
 */
@DisplayName("AudioWaveformMapSerializer round-trips amplitudes and cached width")
internal class AudioWaveformSerializerRoundTripTest : StringSpec({

    val json = Json { prettyPrint = false }

    "AudioWaveformMapSerializer round-trips amplitudes and cached width" {
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val waveform = ScalableAudioWaveform(10, realAudioPath)
        waveform.amplitudes(780, 335)

        val encoded = json.encodeToString(AudioWaveformMapSerializer, mapOf(10 to waveform as AudioWaveform))
        encoded shouldContain "\"cachedWidth\":780"
        encoded shouldContain "\"normalizedAmplitudes\":"

        val decoded = json.decodeFromString(AudioWaveformMapSerializer, encoded)
        val decodedWaveform = decoded.getValue(10)

        decodedWaveform.id shouldBe 10
        decodedWaveform.audioFilePath shouldBe waveform.audioFilePath

        // Re-encoding the decoded instance must reproduce the original JSON byte-for-byte, proving the
        // cached width and the full normalized-amplitudes FloatArray survived the round trip.
        val reEncoded = json.encodeToString(AudioWaveformMapSerializer, mapOf(10 to decodedWaveform))
        reEncoded shouldBe encoded
    }

    "AudioWaveformMapSerializer round-trips empty amplitude state without error" {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val waveform = ScalableAudioWaveform(42, realAudioPath)

        val encoded = json.encodeToString(AudioWaveformMapSerializer, mapOf(42 to waveform as AudioWaveform))
        encoded shouldContain "\"cachedWidth\":0"
        encoded shouldContain "\"normalizedAmplitudes\":\"\""

        val decoded = json.decodeFromString(AudioWaveformMapSerializer, encoded)
        val decodedWaveform = decoded.getValue(42)

        decodedWaveform.id shouldBe 42
        decodedWaveform.audioFilePath shouldBe waveform.audioFilePath

        val reEncoded = json.encodeToString(AudioWaveformMapSerializer, mapOf(42 to decodedWaveform))
        reEncoded shouldBe encoded
    }

    "AudioWaveformMapSerializer round-trips through JsonFileRepository" {
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val waveform = ScalableAudioWaveform(7, realAudioPath)
        waveform.amplitudes(512, 200)

        val storeFile = tempfile("waveforms", ".json")
        JsonFileRepository(storeFile, AudioWaveformMapSerializer).use { repository ->
            repository.add(waveform)
        }

        JsonFileRepository(storeFile, AudioWaveformMapSerializer).use { reloaded ->
            val loaded = reloaded.findById(7).orElseThrow()
            loaded.id shouldBe 7
            loaded.audioFilePath shouldBe waveform.audioFilePath

            val originalJson = json.encodeToString(AudioWaveformMapSerializer, mapOf(7 to waveform as AudioWaveform))
            val reloadedJson = json.encodeToString(AudioWaveformMapSerializer, mapOf(7 to loaded))
            reloadedJson shouldBe originalJson
        }
    }

    "AudioWaveformMapSerializer writes audioFilePath as file:// URI" {
        val realAudioPath = Arb.realAudioFile(ID3_V_24).next()
        val waveform = ScalableAudioWaveform(11, realAudioPath)

        val encoded = json.encodeToString(AudioWaveformMapSerializer, mapOf(11 to waveform as AudioWaveform))

        encoded shouldContain "\"audioFilePath\":\"file://"
        encoded shouldContain realAudioPath.toJsonUri()
    }
})