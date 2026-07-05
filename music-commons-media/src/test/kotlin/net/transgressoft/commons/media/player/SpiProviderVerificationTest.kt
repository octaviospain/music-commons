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

package net.transgressoft.commons.media.player

import net.transgressoft.commons.media.util.loadAudioFileReaders
import net.transgressoft.commons.music.audio.ArbitraryAudioFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as shouldContainString
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/**
 * Runtime verification that all 7 audio format SPI providers are installed
 * and operational through JavaSound's [AudioSystem] facade.
 *
 * Uses real test fixture files from music-commons-test resources.
 */
internal class SpiProviderVerificationTest : FunSpec({

    fun testFile(name: String) = ArbitraryAudioFile.getResourceAsFile("/testfiles/$name")

    context("JavaSound SPI provider installation") {
        test("AudioSystem.getAudioFileTypes() is not empty — SPI providers are registered") {
            val types = AudioSystem.getAudioFileTypes()
            types.shouldNotBeEmpty()
            val typeStrings = types.map { "${it.extension}/$it" }
            typeStrings.any { it.contains("wav", ignoreCase = true) } shouldBe true
        }
    }

    data class DecodeCase(val fixture: String, val assertion: (AudioFormat) -> Unit)

    context("Audio format decoding via AudioSystem.getAudioInputStream()") {
        withData(
            mapOf(
                // Critical: de.sfuhrm:jaad must not throw NPE on WAV input — jaad must not poison WAV.
                "WAV via built-in JDK SPI (jaad must not poison WAV)" to
                    DecodeCase("testeable.wav") { format ->
                        format.channels shouldBe 2
                        format.sampleRate shouldBe 44100.0f
                    },
                "MP3 via mp3spi SPI" to
                    DecodeCase("testeable.mp3") { format -> format.toString() shouldContainString "MPEG" },
                "FLAC via javasound-flac SPI" to
                    DecodeCase("testeable.flac") { format -> format.toString().lowercase() shouldContainString "flac" },
                "M4A/AAC via javasound-aac + de.sfuhrm:jaad SPI" to
                    DecodeCase("testeable_aac.m4a") { format -> format.channels shouldBe 2 },
                "OGG via javasound-vorbis SPI" to
                    DecodeCase("testeable.ogg") { format -> format.sampleRate shouldBe 44100.0f },
                "ALAC-in-M4A via javasound-alac SPI" to
                    DecodeCase("testeable_alac.m4a") { format -> format.channels shouldBe 2 },
                "Opus-in-OGG via jse-spi-opus SPI" to
                    DecodeCase("testeable_opus.ogg") { format -> format.channels shouldBe 2 }
            )
        ) { (fixture, assertion) ->
            AudioSystem.getAudioInputStream(testFile(fixture)).use { stream ->
                assertion(stream.format)
            }
        }

        // testeable_opus.m4a is intentionally NOT tested here.
        // Opus-in-M4A has no pure-Java Maven Central SPI support (ecosystem gap).
        // See AudioDecoderUtil KDoc for the documented limitation.
    }

    context("SPI registry enumeration") {
        test("Core format extensions (WAV, FLAC, OGG) are recognized in getAudioFileTypes()") {
            val extensions =
                AudioSystem.getAudioFileTypes()
                    .map { it.extension.lowercase() }
                    .toSet()

            // WAV is always present (JDK built-in), FLAC via javasound-flac, OGG via javasound-vorbis.
            extensions shouldContain "wav"
            extensions shouldContain "flac"
            extensions shouldContain "ogg"

            // MP3 and M4A/MP4 SPIs (mp3spi, javasound-aac) handle stream conversion via
            // getAudioInputStream() but do NOT register in getAudioFileTypes(). The decode tests
            // above verify they work correctly.
        }

        test("loaded AudioFileReader SPI class names include the mp3 and aac routing providers") {
            val classNames = loadAudioFileReaders().map { it.javaClass.name }
            classNames.shouldNotBeEmpty()
            classNames shouldContain "javazoom.spi.mpeg.sampled.file.MpegAudioFileReader"
            classNames shouldContain "net.sourceforge.jaad.spi.javasound.AACAudioFileReader"
        }
    }
})