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
import io.kotest.datatest.withData
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

    withData(
        nameFn = { (codec, supported) -> "AudioFileCodec $codec isSupportedBySpi=$supported" },
        AudioFileCodec.MP3 to true,
        AudioFileCodec.AAC to true,
        AudioFileCodec.PCM to true,
        AudioFileCodec.FLAC to true,
        AudioFileCodec.VORBIS to true,
        AudioFileCodec.ALAC to false,
        AudioFileCodec.OPUS to false
    ) { (codec, supported) ->
        codec.isSupportedBySpi shouldBe supported
    }

    withData(
        nameFn = { (codec, ext) -> "AudioFileCodec $codec defaultExtension=$ext" },
        AudioFileCodec.MP3 to "mp3",
        AudioFileCodec.AAC to "m4a",
        AudioFileCodec.ALAC to "m4a",
        AudioFileCodec.PCM to "wav",
        AudioFileCodec.FLAC to "flac",
        AudioFileCodec.VORBIS to "ogg",
        AudioFileCodec.OPUS to "ogg"
    ) { (codec, ext) ->
        codec.defaultExtension shouldBe ext
    }

    "AudioFileType all file types have extensions" {
        AudioFileType.extensions shouldContainExactly
            listOf("mp3", "m4a", "wav", "flac", "ogg")
    }

    withData(
        nameFn = { (ext, type) -> "AudioFileType fromExtension '$ext' -> $type" },
        "mp3" to AudioFileType.MP3,
        "m4a" to AudioFileType.M4A,
        "wav" to AudioFileType.WAV,
        "flac" to AudioFileType.FLAC,
        "ogg" to AudioFileType.OGG,
        "MP3" to AudioFileType.MP3,
        "unknown" to null
    ) { (ext, type) ->
        AudioFileType.fromExtension(ext) shouldBe type
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

    withData(
        nameFn = { "AudioFileType $it isPrimaryCodecSupported" },
        AudioFileType.MP3,
        AudioFileType.M4A,
        AudioFileType.WAV,
        AudioFileType.FLAC,
        AudioFileType.OGG
    ) { type ->
        type.isPrimaryCodecSupported() shouldBe true
    }

    withData(
        nameFn = { (ext, type) -> "toAudioFileType '$ext' -> $type" },
        "mp3" to AudioFileType.MP3,
        "m4a" to AudioFileType.M4A,
        "wav" to AudioFileType.WAV,
        "flac" to AudioFileType.FLAC,
        "ogg" to AudioFileType.OGG
    ) { (ext, type) ->
        ext.toAudioFileType() shouldBe type
    }

    "toAudioFileType throws for unsupported extensions" {
        val ex =
            shouldThrow<UnsupportedOperationException> {
                "aac".toAudioFileType()
            }
        ex.message shouldBe "'aac' is not a supported audio file extension"
    }
})