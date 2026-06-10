package net.transgressoft.commons.media.util

import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException
import javax.sound.sampled.spi.AudioFileReader

private val PCM_FORMAT = AudioFormat(44_100f, 16, 1, true, false)

internal class AudioDecoderUtilRegressionTest : StringSpec({

    fun resourceToTemp(name: String): java.nio.file.Path {
        val stream =
            javaClass.getResourceAsStream("/testfiles/$name")
                ?: throw IllegalArgumentException("Resource not found: $name")
        return stream.use {
            val temp = Files.createTempFile("audio_", ".${name.substringAfterLast('.')}")
            Files.copy(it, temp, StandardCopyOption.REPLACE_EXISTING)
            temp
        }
    }

    fun pcmStreamOf(vararg bytes: Byte): AudioInputStream =
        AudioInputStream(ByteArrayInputStream(bytes), PCM_FORMAT, bytes.size.toLong() / PCM_FORMAT.frameSize.toLong())

    fun audioFileFormat(): AudioFileFormat =
        AudioFileFormat(AudioFileFormat.Type.WAVE, PCM_FORMAT, 44_100)

    fun crashingStream(): AudioInputStream =
        object : AudioInputStream(ByteArrayInputStream(ByteArray(0)), PCM_FORMAT, AudioSystem.NOT_SPECIFIED.toLong()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw NegativeArraySizeException("bad decode")

            override fun read(): Int = throw NegativeArraySizeException("bad decode")
        }

    fun ioFailingStream(): AudioInputStream =
        AudioInputStream(
            object : InputStream() {
                override fun read(): Int = throw IOException("bad decode")

                override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("bad decode")
            },
            PCM_FORMAT,
            AudioSystem.NOT_SPECIFIED.toLong()
        )

    fun validateReadablePcmStream(stream: AudioInputStream): AudioInputStream =
        net.transgressoft.commons.media.util.validateReadablePcmStream(stream)

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
            deleteDecodedTempFile(temp)
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
            deleteDecodedTempFile(temp)
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
            deleteDecodedTempFile(temp)
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
            deleteDecodedTempFile(temp)
        }
    }

    "validateReadablePcmStream preserves the decoded frame length" {
        val stream = AudioInputStream(ByteArrayInputStream(ByteArray(8_820)), PCM_FORMAT, 4_410)

        val wrapped = validateReadablePcmStream(stream)

        wrapped.frameLength shouldBe 4_410
        wrapped.close()
    }

    "decodeToPcmStream skips a crashing provider and uses the next SPI reader" {
        val crashingReader =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioInputStream(any<File>()) } returns crashingStream()
            }
        val successReader =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioInputStream(any<File>()) } returns pcmStreamOf(0x00, 0x40, 0x00, 0xC0.toByte(), 0x00, 0x20, 0x00, 0xE0.toByte())
            }

        mockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        every { loadAudioFileReaders() } returns listOf(crashingReader, successReader)

        val temp = Files.createTempFile("audio_", ".mp3")
        try {
            val decoded = decodeToPcmStream(temp)
            val buffer = ByteArray(8)
            val bytesRead = decoded.read(buffer)

            decoded.format shouldBe PCM_FORMAT
            bytesRead shouldBeGreaterThan 0
        } finally {
            deleteDecodedTempFile(temp)
            unmockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        }
    }

    "decodeToPcmStream skips a provider that throws IOException while probing PCM data" {
        val ioFailingReader =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioInputStream(any<File>()) } returns ioFailingStream()
            }
        val successReader =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioInputStream(any<File>()) } returns pcmStreamOf(0x00, 0x40, 0x00, 0xC0.toByte(), 0x00, 0x20, 0x00, 0xE0.toByte())
            }

        mockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        every { loadAudioFileReaders() } returns listOf(ioFailingReader, successReader)

        val temp = Files.createTempFile("audio_", ".mp3")
        try {
            val decoded = decodeToPcmStream(temp)
            val buffer = ByteArray(8)
            val bytesRead = decoded.read(buffer)

            decoded.format shouldBe PCM_FORMAT
            bytesRead shouldBeGreaterThan 0
        } finally {
            deleteDecodedTempFile(temp)
            unmockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        }
    }

    "decodeToPcmStream reports unsupported playback when every provider fails" {
        val crashingReader1 =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioInputStream(any<File>()) } returns crashingStream()
            }
        val crashingReader2 =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioInputStream(any<File>()) } returns crashingStream()
            }

        mockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        every { loadAudioFileReaders() } returns listOf(crashingReader1, crashingReader2)

        val temp = Files.createTempFile("audio_", ".mp3")
        try {
            val error =
                shouldThrow<UnsupportedAudioPlaybackException> {
                    decodeToPcmStream(temp)
                }

            error.message shouldContain "tried 2 providers"
        } finally {
            deleteDecodedTempFile(temp)
            unmockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        }
    }

    "readAudioFileFormat skips a crashing provider and uses the next SPI reader" {
        val crashingReader =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioFileFormat(any<File>()) } throws NullPointerException("bad metadata")
            }
        val successReader =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioFileFormat(any<File>()) } returns audioFileFormat()
            }

        mockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        every { loadAudioFileReaders() } returns listOf(crashingReader, successReader)

        val temp = Files.createTempFile("audio_", ".m4a")
        try {
            val format = readAudioFileFormat(temp)

            format.type shouldBe AudioFileFormat.Type.WAVE
        } finally {
            deleteDecodedTempFile(temp)
            unmockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        }
    }

    "readAudioFileFormat reports unsupported playback when every provider rejects the file" {
        val rejectingReader1 =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioFileFormat(any<File>()) } throws UnsupportedAudioFileException("bad metadata")
            }
        val rejectingReader2 =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioFileFormat(any<File>()) } throws UnsupportedAudioFileException("bad metadata")
            }

        mockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        every { loadAudioFileReaders() } returns listOf(rejectingReader1, rejectingReader2)

        val temp = Files.createTempFile("audio_", ".m4a")
        try {
            val error =
                shouldThrow<UnsupportedAudioPlaybackException> {
                    readAudioFileFormat(temp)
                }

            error.message shouldContain "tried 2 providers"
        } finally {
            deleteDecodedTempFile(temp)
            unmockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        }
    }

    "readerPriority prefers mp3spi over JAAD for mp3 files" {
        val mp3spiClass = "javazoom.spi.mpeg.sampled.file.MpegAudioFileReader"
        val jaadClass = "net.sourceforge.jaad.spi.javasound.AACAudioFileReader"

        readerPriority(File("track.mp3"), mp3spiClass) shouldBeLessThan readerPriority(File("track.mp3"), jaadClass)
    }

    "readerPriority prefers JAAD over mp3spi for m4a files" {
        val mp3spiClass = "javazoom.spi.mpeg.sampled.file.MpegAudioFileReader"
        val jaadClass = "net.sourceforge.jaad.spi.javasound.AACAudioFileReader"

        readerPriority(File("track.m4a"), jaadClass) shouldBeLessThan readerPriority(File("track.m4a"), mp3spiClass)
    }

    "readerPriority prefers javasound-alac over JAAD for m4a files" {
        val alacClass = "com.beatofthedrum.alacdecoder.spi.AlacAudioFileReader"
        val jaadClass = "net.sourceforge.jaad.spi.javasound.AACAudioFileReader"

        readerPriority(File("track.m4a"), alacClass) shouldBeLessThan readerPriority(File("track.m4a"), jaadClass)
    }

    "ALAC M4A decodes to non-empty PCM via decodeToPcmStream" {
        val temp = resourceToTemp("testeable_alac.m4a")
        try {
            val pcmStream = decodeToPcmStream(temp)
            val format = pcmStream.format

            format.encoding.toString() shouldBe "PCM_SIGNED"
            format.sampleSizeInBits shouldBe 16

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
            deleteDecodedTempFile(temp)
        }
    }

    "Opus OGG decodes to non-empty PCM via decodeToPcmStream" {
        val temp = resourceToTemp("testeable_opus.ogg")
        try {
            val pcmStream = decodeToPcmStream(temp)
            val format = pcmStream.format

            format.encoding.toString() shouldBe "PCM_SIGNED"
            format.sampleSizeInBits shouldBe 16

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
            deleteDecodedTempFile(temp)
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
            deleteDecodedTempFile(temp)
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
            deleteDecodedTempFile(temp)
        }
    }
})