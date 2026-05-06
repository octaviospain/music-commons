package net.transgressoft.commons.media.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AudioDecoderUtilRegressionTest : StringSpec({

    fun resourceToTemp(name: String): java.nio.file.Path {
        val stream =
            javaClass.getResourceAsStream("/testfiles/$name")
                ?: throw IllegalArgumentException("Resource not found: $name")
        val temp = Files.createTempFile("audio_", "_${name.substringAfterLast('.')}")
        Files.copy(stream, temp, StandardCopyOption.REPLACE_EXISTING)
        stream.close()
        return temp
    }

    "AAC M4A decodes to valid PCM with correct endianness and known sample size" {
        val temp = resourceToTemp("testeable_aac.m4a")
        try {
            val pcmStream = decodeToPcmStream(temp)
            val format = pcmStream.format

            format.encoding.toString() shouldBe "PCM_SIGNED"
            format.sampleSizeInBits shouldBe 16
            format.isBigEndian shouldBe true
            format.channels shouldBe 2

            val buffer = ByteArray(8192)
            var total = 0
            var read = pcmStream.read(buffer)
            while (read != -1) {
                if (read > 0) total += read
                read = pcmStream.read(buffer)
            }
            pcmStream.close()
            total shouldBeGreaterThan 0
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    "AAC128 M4A decodes to valid PCM with correct endianness and known sample size" {
        val temp = resourceToTemp("testeable_aac128.m4a")
        try {
            val pcmStream = decodeToPcmStream(temp)
            val format = pcmStream.format

            format.encoding.toString() shouldBe "PCM_SIGNED"
            format.sampleSizeInBits shouldBe 16
            format.isBigEndian shouldBe true
            format.channels shouldBe 2

            val buffer = ByteArray(8192)
            var total = 0
            var read = pcmStream.read(buffer)
            while (read != -1) {
                if (read > 0) total += read
                read = pcmStream.read(buffer)
            }
            pcmStream.close()
            total shouldBeGreaterThan 0
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    "OGG Vorbis converts without UnsupportedAudioFileException" {
        val temp = resourceToTemp("testeable_vorbis.ogg")
        try {
            val pcmStream = decodeToPcmStream(temp)
            val format = pcmStream.format

            format.encoding.toString() shouldBe "PCM_SIGNED"
            format.sampleSizeInBits shouldBe 16
            format.channels shouldBe 2

            val buffer = ByteArray(8192)
            var total = 0
            var read = pcmStream.read(buffer)
            while (read != -1) {
                if (read > 0) total += read
                read = pcmStream.read(buffer)
            }
            pcmStream.close()
            total shouldBeGreaterThan 0
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    "MP3 converts to PCM with known sample size" {
        val temp = resourceToTemp("testeable.mp3")
        try {
            val pcmStream = decodeToPcmStream(temp)
            val format = pcmStream.format

            format.encoding.toString() shouldBe "PCM_SIGNED"
            format.sampleSizeInBits shouldBe 16
            format.channels shouldBe 2

            val buffer = ByteArray(8192)
            var total = 0
            var read = pcmStream.read(buffer)
            while (read != -1) {
                if (read > 0) total += read
                read = pcmStream.read(buffer)
            }
            pcmStream.close()
            total shouldBeGreaterThan 0
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    "FLAC converts to PCM with known sample size" {
        val temp = resourceToTemp("testeable.flac")
        try {
            val pcmStream = decodeToPcmStream(temp)
            val format = pcmStream.format

            format.encoding.toString() shouldBe "PCM_SIGNED"
            format.sampleSizeInBits shouldBe 16
            format.channels shouldBe 2

            val buffer = ByteArray(8192)
            var total = 0
            var read = pcmStream.read(buffer)
            while (read != -1) {
                if (read > 0) total += read
                read = pcmStream.read(buffer)
            }
            pcmStream.close()
            total shouldBeGreaterThan 0
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    "WAV converts to PCM with known sample size" {
        val temp = resourceToTemp("testeable.wav")
        try {
            val pcmStream = decodeToPcmStream(temp)
            val format = pcmStream.format

            format.encoding.toString() shouldBe "PCM_SIGNED"
            format.sampleSizeInBits shouldBe 16
            format.channels shouldBe 2

            val buffer = ByteArray(8192)
            var total = 0
            var read = pcmStream.read(buffer)
            while (read != -1) {
                if (read > 0) total += read
                read = pcmStream.read(buffer)
            }
            pcmStream.close()
            total shouldBeGreaterThan 0
        } finally {
            Files.deleteIfExists(temp)
        }
    }
})