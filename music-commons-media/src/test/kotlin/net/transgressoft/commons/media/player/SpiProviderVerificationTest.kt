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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as shouldContainString
import javax.sound.sampled.AudioSystem

/**
 * Runtime verification that all 7 audio format SPI providers are installed
 * and operational through JavaSound's [AudioSystem] facade.
 *
 * Uses real test fixture files from music-commons-test resources.
 */
internal class SpiProviderVerificationTest : FunSpec({

    context("JavaSound SPI provider installation") {

        test("AudioSystem.getAudioFileTypes() is not empty — SPI providers are registered") {
            val types = AudioSystem.getAudioFileTypes()
            types.shouldNotBeEmpty()
            val typeStrings = types.map { "${it.extension}/$it" }
            typeStrings.any { it.contains("wav", ignoreCase = true) } shouldBe true
        }
    }

    context("Audio format decoding via AudioSystem.getAudioInputStream()") {

        val getTestFile = { name: String ->
            ArbitraryAudioFile.getResourceAsFile("/testfiles/$name")
        }

        test("WAV decodes via built-in JDK SPI (validates jaad does NOT poison WAV)") {
            // Critical: de.sfuhrm:jaad must not throw NPE on WAV input (spike 008 case 4b)
            val file = getTestFile("testeable.wav")
            val stream = AudioSystem.getAudioInputStream(file)
            stream.format.channels shouldBe 2
            stream.format.sampleRate shouldBe 44100.0f
            stream.close()
        }

        test("MP3 decodes via mp3spi SPI") {
            val file = getTestFile("testeable.mp3")
            val stream = AudioSystem.getAudioInputStream(file)
            stream.format.toString() shouldContainString "MPEG"
            stream.close()
        }

        test("FLAC decodes via javasound-flac SPI") {
            val file = getTestFile("testeable.flac")
            val stream = AudioSystem.getAudioInputStream(file)
            stream.format.toString().lowercase() shouldContainString "flac"
            stream.close()
        }

        test("M4A/AAC decodes via javasound-aac + de.sfuhrm:jaad SPI") {
            val file = getTestFile("testeable_aac.m4a")
            val stream = AudioSystem.getAudioInputStream(file)
            stream.format.channels shouldBe 2
            stream.close()
        }

        test("OGG decodes via javasound-vorbis SPI") {
            val file = getTestFile("testeable.ogg")
            val stream = AudioSystem.getAudioInputStream(file)
            stream.format.sampleRate shouldBe 44100.0f
            stream.close()
        }

        test("ALAC-in-M4A decodes via javasound-alac SPI") {
            val file = getTestFile("testeable_alac.m4a")
            val stream = AudioSystem.getAudioInputStream(file)
            stream.format.channels shouldBe 2
            stream.close()
        }

        test("Opus-in-OGG decodes via jse-spi-opus SPI") {
            val file = getTestFile("testeable_opus.ogg")
            val stream = AudioSystem.getAudioInputStream(file)
            stream.format.channels shouldBe 2
            stream.close()
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

            // WAV is always present (JDK built-in)
            extensions shouldContain "wav"
            // FLAC via javasound-flac
            extensions shouldContain "flac"
            // OGG via javasound-vorbis
            extensions shouldContain "ogg"

            // Note: MP3 and M4A/MP4 SPIs (mp3spi, javasound-aac) handle stream conversion
            // via getAudioInputStream() but do NOT register in getAudioFileTypes().
            // This is a known limitation of these SPI implementations.
            // The individual decode tests above verify they work correctly.
        }

        test("loaded AudioFileReader SPI class names are recorded for routing") {
            val readers = loadAudioFileReaders()
            readers.shouldNotBeEmpty()
            val classNames = readers.map { it.javaClass.name }
            println("Loaded AudioFileReader SPI providers:")
            classNames.forEach { println("  - $it") }
        }
    }
})