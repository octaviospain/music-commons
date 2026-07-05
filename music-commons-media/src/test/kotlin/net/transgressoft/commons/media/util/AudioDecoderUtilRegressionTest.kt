package net.transgressoft.commons.media.util

import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
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
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException
import javax.sound.sampled.spi.AudioFileReader

private val PCM_FORMAT = AudioFormat(44_100f, 16, 1, true, false)

private fun pcmStreamOf(vararg bytes: Byte): AudioInputStream =
    AudioInputStream(ByteArrayInputStream(bytes), PCM_FORMAT, bytes.size.toLong() / PCM_FORMAT.frameSize.toLong())

private fun crashingStream(): AudioInputStream =
    object : AudioInputStream(ByteArrayInputStream(ByteArray(0)), PCM_FORMAT, AudioSystem.NOT_SPECIFIED.toLong()) {
        override fun read(b: ByteArray, off: Int, len: Int): Int = throw NegativeArraySizeException("bad decode")

        override fun read(): Int = throw NegativeArraySizeException("bad decode")
    }

private fun ioFailingStream(): AudioInputStream =
    AudioInputStream(
        object : InputStream() {
            override fun read(): Int = throw IOException("bad decode")

            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("bad decode")
        },
        PCM_FORMAT,
        AudioSystem.NOT_SPECIFIED.toLong()
    )

/**
 * Runs [block] with [loadAudioFileReaders] statically stubbed to return [readers], always
 * restoring the real SPI lookup afterwards so fault-injection tests stay isolated.
 */
private fun withReaders(vararg readers: AudioFileReader, block: () -> Unit) {
    mockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
    every { loadAudioFileReaders() } returns readers.toList()
    try {
        block()
    } finally {
        unmockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
    }
}

private fun decodingReader(stream: () -> AudioInputStream): AudioFileReader =
    mockk(relaxed = true) {
        every { getAudioInputStream(any<File>()) } returns stream()
    }

internal class AudioDecoderUtilRegressionTest : FunSpec({

    data class DecodeCase(val fixture: String, val expectedChannels: Int?)

    context("decodeToPcmStream produces valid 16-bit signed PCM per codec") {
        withData(
            mapOf(
                "AAC M4A" to DecodeCase("testeable_aac.m4a", expectedChannels = 2),
                "AAC128 M4A" to DecodeCase("testeable_aac128.m4a", expectedChannels = 2),
                "OGG Vorbis" to DecodeCase("testeable_vorbis.ogg", expectedChannels = 2),
                "MP3" to DecodeCase("testeable.mp3", expectedChannels = 2),
                // ALAC/Opus decoders do not expose a stable channel count on this fixture set;
                // assert only the fields their original tests did.
                "ALAC M4A" to DecodeCase("testeable_alac.m4a", expectedChannels = null),
                "Opus OGG" to DecodeCase("testeable_opus.ogg", expectedChannels = null),
                "FLAC" to DecodeCase("testeable.flac", expectedChannels = 2),
                "WAV" to DecodeCase("testeable.wav", expectedChannels = 2)
            )
        ) { (fixture, expectedChannels) ->
            val temp = resourceToTemp(fixture)
            try {
                val pcmStream = decodeToPcmStream(temp)
                val format = pcmStream.format

                assertSoftly {
                    format.encoding.toString() shouldBe "PCM_SIGNED"
                    format.sampleSizeInBits shouldBe 16
                    expectedChannels?.let { format.channels shouldBe it }
                    if (fixture.endsWith(".m4a") && expectedChannels != null) {
                        format.isBigEndian shouldBe true
                    }
                }
                pcmStream.drainAndCount() shouldBeGreaterThan 0
            } finally {
                deleteDecodedTempFile(temp)
            }
        }
    }

    test("validateReadablePcmStream preserves the decoded frame length") {
        val stream = AudioInputStream(ByteArrayInputStream(ByteArray(8_820)), PCM_FORMAT, 4_410)

        val wrapped = validateReadablePcmStream(stream)

        wrapped.frameLength shouldBe 4_410
        wrapped.close()
    }

    test("decodeToPcmStream skips a crashing provider and uses the next SPI reader") {
        val successReader = decodingReader { pcmStreamOf(0x00, 0x40, 0x00, 0xC0.toByte(), 0x00, 0x20, 0x00, 0xE0.toByte()) }

        val temp = Files.createTempFile("audio_", ".mp3")
        try {
            withReaders(decodingReader(::crashingStream), successReader) {
                val decoded = decodeToPcmStream(temp)
                val bytesRead = decoded.read(ByteArray(8))

                decoded.format shouldBe PCM_FORMAT
                bytesRead shouldBeGreaterThan 0
            }
        } finally {
            deleteDecodedTempFile(temp)
        }
    }

    test("decodeToPcmStream skips a provider that throws IOException while probing PCM data") {
        val successReader = decodingReader { pcmStreamOf(0x00, 0x40, 0x00, 0xC0.toByte(), 0x00, 0x20, 0x00, 0xE0.toByte()) }

        val temp = Files.createTempFile("audio_", ".mp3")
        try {
            withReaders(decodingReader(::ioFailingStream), successReader) {
                val decoded = decodeToPcmStream(temp)
                val bytesRead = decoded.read(ByteArray(8))

                decoded.format shouldBe PCM_FORMAT
                bytesRead shouldBeGreaterThan 0
            }
        } finally {
            deleteDecodedTempFile(temp)
        }
    }

    test("decodeToPcmStream reports unsupported playback when every provider fails") {
        val temp = Files.createTempFile("audio_", ".mp3")
        try {
            withReaders(decodingReader(::crashingStream), decodingReader(::crashingStream)) {
                val error =
                    shouldThrow<UnsupportedAudioPlaybackException> {
                        decodeToPcmStream(temp)
                    }

                error.message shouldContain "tried 2 providers"
            }
        } finally {
            deleteDecodedTempFile(temp)
        }
    }

    test("readAudioFileFormat skips a crashing provider and uses the next SPI reader") {
        val crashingReader =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioFileFormat(any<File>()) } throws NullPointerException("bad metadata")
            }
        val successReader =
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioFileFormat(any<File>()) } returns AudioFileFormat(AudioFileFormat.Type.WAVE, PCM_FORMAT, 44_100)
            }

        val temp = Files.createTempFile("audio_", ".m4a")
        try {
            withReaders(crashingReader, successReader) {
                readAudioFileFormat(temp).type shouldBe AudioFileFormat.Type.WAVE
            }
        } finally {
            deleteDecodedTempFile(temp)
        }
    }

    test("readAudioFileFormat reports unsupported playback when every provider rejects the file") {
        val rejectingReader = {
            mockk<AudioFileReader>(relaxed = true) {
                every { getAudioFileFormat(any<File>()) } throws UnsupportedAudioFileException("bad metadata")
            }
        }

        val temp = Files.createTempFile("audio_", ".m4a")
        try {
            withReaders(rejectingReader(), rejectingReader()) {
                val error =
                    shouldThrow<UnsupportedAudioPlaybackException> {
                        readAudioFileFormat(temp)
                    }

                error.message shouldContain "tried 2 providers"
            }
        } finally {
            deleteDecodedTempFile(temp)
        }
    }

    data class PriorityCase(val file: String, val preferred: String, val deprioritized: String)

    context("readerPriority routes files to the correct SPI provider") {
        val mp3spi = "javazoom.spi.mpeg.sampled.file.MpegAudioFileReader"
        val jaad = "net.sourceforge.jaad.spi.javasound.AACAudioFileReader"
        val alac = "com.beatofthedrum.alacdecoder.spi.AlacAudioFileReader"
        withData(
            nameFn = { "${it.file} prefers ${it.preferred.substringAfterLast('.')}" },
            PriorityCase("track.mp3", preferred = mp3spi, deprioritized = jaad),
            PriorityCase("track.m4a", preferred = jaad, deprioritized = mp3spi),
            PriorityCase("track.m4a", preferred = alac, deprioritized = jaad)
        ) { (file, preferred, deprioritized) ->
            readerPriority(File(file), preferred) shouldBeLessThan readerPriority(File(file), deprioritized)
        }
    }
})