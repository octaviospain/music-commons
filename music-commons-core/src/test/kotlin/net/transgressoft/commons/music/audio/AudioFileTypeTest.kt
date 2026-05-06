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

package net.transgressoft.commons.music.audio

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

@DisplayName("AudioFileType and AudioFileCodec")
internal class AudioFileTypeTest : StringSpec({

    "AudioFileCodec supported codecs are MP3, AAC, PCM, FLAC, VORBIS" {
        AudioFileCodec.supportedCodecs.map { it.name } shouldContainExactly
            listOf("MP3", "AAC", "PCM", "FLAC", "VORBIS")
    }

    "AudioFileCodec unsupported codecs are ALAC and OPUS" {
        AudioFileCodec.unsupportedCodecs.map { it.name } shouldContainExactly
            listOf("ALAC", "OPUS")
    }

    "AudioFileCodec each codec has correct SPI support flag" {
        AudioFileCodec.MP3.isSupportedBySpi shouldBe true
        AudioFileCodec.AAC.isSupportedBySpi shouldBe true
        AudioFileCodec.PCM.isSupportedBySpi shouldBe true
        AudioFileCodec.FLAC.isSupportedBySpi shouldBe true
        AudioFileCodec.VORBIS.isSupportedBySpi shouldBe true
        AudioFileCodec.ALAC.isSupportedBySpi shouldBe false
        AudioFileCodec.OPUS.isSupportedBySpi shouldBe false
    }

    "AudioFileCodec each codec has correct default extension" {
        AudioFileCodec.MP3.defaultExtension shouldBe "mp3"
        AudioFileCodec.AAC.defaultExtension shouldBe "m4a"
        AudioFileCodec.ALAC.defaultExtension shouldBe "m4a"
        AudioFileCodec.PCM.defaultExtension shouldBe "wav"
        AudioFileCodec.FLAC.defaultExtension shouldBe "flac"
        AudioFileCodec.VORBIS.defaultExtension shouldBe "ogg"
        AudioFileCodec.OPUS.defaultExtension shouldBe "ogg"
    }

    "AudioFileType all file types have extensions" {
        AudioFileType.extensions shouldContainExactly
            listOf("mp3", "m4a", "wav", "flac", "ogg")
    }

    "AudioFileType fromExtension returns correct type" {
        AudioFileType.fromExtension("mp3") shouldBe AudioFileType.MP3
        AudioFileType.fromExtension("m4a") shouldBe AudioFileType.M4A
        AudioFileType.fromExtension("wav") shouldBe AudioFileType.WAV
        AudioFileType.fromExtension("flac") shouldBe AudioFileType.FLAC
        AudioFileType.fromExtension("ogg") shouldBe AudioFileType.OGG
        AudioFileType.fromExtension("MP3") shouldBe AudioFileType.MP3
        AudioFileType.fromExtension("unknown") shouldBe null
    }

    "AudioFileType M4A supports both AAC and ALAC codecs" {
        AudioFileType.M4A.possibleCodecs shouldContainExactly
            listOf(AudioFileCodec.AAC, AudioFileCodec.ALAC)
        AudioFileType.M4A.supportsCodec(AudioFileCodec.AAC) shouldBe true
        AudioFileType.M4A.supportsCodec(AudioFileCodec.ALAC) shouldBe true
        AudioFileType.M4A.primaryCodec shouldBe AudioFileCodec.AAC
    }

    "AudioFileType OGG supports both VORBIS and OPUS codecs" {
        AudioFileType.OGG.possibleCodecs shouldContainExactly
            listOf(AudioFileCodec.VORBIS, AudioFileCodec.OPUS)
        AudioFileType.OGG.supportsCodec(AudioFileCodec.VORBIS) shouldBe true
        AudioFileType.OGG.supportsCodec(AudioFileCodec.OPUS) shouldBe true
        AudioFileType.OGG.primaryCodec shouldBe AudioFileCodec.VORBIS
    }

    "AudioFileType single-codec formats only list one codec" {
        AudioFileType.MP3.possibleCodecs shouldHaveSize 1
        AudioFileType.MP3.possibleCodecs[0] shouldBe AudioFileCodec.MP3
        AudioFileType.WAV.possibleCodecs shouldHaveSize 1
        AudioFileType.WAV.possibleCodecs[0] shouldBe AudioFileCodec.PCM
        AudioFileType.FLAC.possibleCodecs shouldHaveSize 1
        AudioFileType.FLAC.possibleCodecs[0] shouldBe AudioFileCodec.FLAC
    }

    "AudioFileType isPrimaryCodecSupported returns true for all current formats" {
        AudioFileType.MP3.isPrimaryCodecSupported() shouldBe true
        AudioFileType.M4A.isPrimaryCodecSupported() shouldBe true
        AudioFileType.WAV.isPrimaryCodecSupported() shouldBe true
        AudioFileType.FLAC.isPrimaryCodecSupported() shouldBe true
        AudioFileType.OGG.isPrimaryCodecSupported() shouldBe true
    }

    "toAudioFileType converts valid extensions to AudioFileType" {
        "mp3".toAudioFileType() shouldBe AudioFileType.MP3
        "m4a".toAudioFileType() shouldBe AudioFileType.M4A
        "wav".toAudioFileType() shouldBe AudioFileType.WAV
        "flac".toAudioFileType() shouldBe AudioFileType.FLAC
        "ogg".toAudioFileType() shouldBe AudioFileType.OGG
    }

    "toAudioFileType throws for unsupported extensions" {
        val ex =
            shouldThrow<UnsupportedOperationException> {
                "aac".toAudioFileType()
            }
        ex.message shouldBe "'aac' is not a supported audio file extension"
    }
})