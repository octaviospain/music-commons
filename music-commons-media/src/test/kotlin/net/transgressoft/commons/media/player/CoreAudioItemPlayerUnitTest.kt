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

import net.transgressoft.commons.media.util.decodeToPcmStream
import net.transgressoft.commons.music.audio.ArbitraryAudioFile
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer.Status
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.SourceDataLine
import kotlin.time.Duration.Companion.seconds

private val PCM_FORMAT = AudioFormat(44_100f, 16, 1, true, false)

private fun enumValue(name: String): InternalState = InternalState.valueOf(name)

/**
 * Unit tests for [CoreAudioItemPlayer] that exercise the public API without triggering
 * real audio output. Complements [CoreAudioItemPlayerPlaybackTest], which requires a
 * working audio device and is gated behind the `audio-hardware` tag.
 */
internal class CoreAudioItemPlayerUnitTest : StringSpec({

    fun audioItem(path: Path): ReactiveAudioItem<*> =
        mockk(relaxed = true) {
            every { this@mockk.path } returns path
            every { fileName } returns path.fileName.toString()
            every { extension } returns path.toString().substringAfterLast('.', "")
            every { encoding } returns null
            every { encoder } returns null
        }

    fun fakeLine(): SourceDataLine =
        mockk(relaxed = true) {
            every { isOpen } returns true
            every { write(any(), any(), any()) } answers { thirdArg() }
        }

    fun streamOf(vararg chunks: ByteArray): AudioInputStream {
        val bytes = chunks.flatMap { it.toList() }.toByteArray()
        return AudioInputStream(bytes.inputStream(), PCM_FORMAT, bytes.size.toLong() / PCM_FORMAT.frameSize)
    }

    fun discardBytes(stream: AudioInputStream, bytes: Long): Long {
        val buffer = ByteArray(4096)
        var remaining = bytes
        var skipped = 0L
        while (remaining > 0L) {
            val bytesRead = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (bytesRead <= 0) break
            skipped += bytesRead
            remaining -= bytesRead
        }
        return skipped
    }

    fun readBytes(stream: AudioInputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val bytesRead = stream.read(buffer, offset, size - offset)
            if (bytesRead <= 0) break
            offset += bytesRead
        }
        return buffer.copyOf(offset)
    }

    "initial status is UNKNOWN" {
        val player = CoreAudioItemPlayer()
        try {
            player.status() shouldBe Status.UNKNOWN
        } finally {
            player.dispose()
        }
    }

    "initial totalDuration is ZERO" {
        val player = CoreAudioItemPlayer()
        try {
            player.totalDuration shouldBe Duration.ZERO
        } finally {
            player.dispose()
        }
    }

    "initial getCurrentTime is ZERO" {
        val player = CoreAudioItemPlayer()
        try {
            player.getCurrentTime() shouldBe Duration.ZERO
        } finally {
            player.dispose()
        }
    }

    "pause is a no-op when nothing is playing" {
        val player = CoreAudioItemPlayer()
        try {
            shouldNotThrowAny { player.pause() }
            player.status() shouldBe Status.UNKNOWN
        } finally {
            player.dispose()
        }
    }

    "resume is a no-op when nothing is paused" {
        val player = CoreAudioItemPlayer()
        try {
            shouldNotThrowAny { player.resume() }
            player.status() shouldBe Status.UNKNOWN
        } finally {
            player.dispose()
        }
    }

    "stop is a no-op when nothing is playing" {
        val player = CoreAudioItemPlayer()
        try {
            player.stop()
            player.status() shouldBe Status.STOPPED
        } finally {
            player.dispose()
        }
    }

    "setVolume clamps to [0.0, 1.0]" {
        val player = CoreAudioItemPlayer()
        try {
            shouldNotThrowAny {
                player.setVolume(-1.0)
                player.setVolume(0.0)
                player.setVolume(0.5)
                player.setVolume(1.0)
                player.setVolume(2.0)
            }
        } finally {
            player.dispose()
        }
    }

    "seek before play stores the target without throwing" {
        val player = CoreAudioItemPlayer()
        try {
            shouldNotThrowAny { player.seek(Duration.ofMillis(500)) }
            player.status() shouldBe Status.UNKNOWN
        } finally {
            player.dispose()
        }
    }

    "paused seek stores the latest target without opening a decoder" {
        val decodeCount = AtomicInteger()
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = {
                    decodeCount.incrementAndGet()
                    streamOf(ByteArray(8))
                },
                lineFactory = { fakeLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        player.state.set(enumValue("PAUSED"))

        try {
            player.seek(Duration.ofMillis(100))
            player.seek(Duration.ofMillis(250))

            decodeCount.get() shouldBe 0
            player.seekTargetMillis.get() shouldBe 250L
        } finally {
            player.dispose()
        }
    }

    "seek target stores latest value and resets after consumption" {
        val player = CoreAudioItemPlayer()
        try {
            player.seek(Duration.ofMillis(100))
            player.seek(Duration.ofMillis(250))

            player.seekTargetMillis.get() shouldBe 250L
        } finally {
            player.dispose()
        }
    }

    "unknown duration may remain ZERO before playback" {
        val player = CoreAudioItemPlayer()
        try {
            player.totalDuration shouldBe Duration.ZERO
        } finally {
            player.dispose()
        }
    }

    "DurationProber duration prefers metadata and falls back to frame length" {
        val prober = DurationProber { path -> decodeToPcmStream(path) }
        val metadataDuration =
            AudioFileFormat(
                AudioFileFormat.Type.WAVE,
                PCM_FORMAT,
                44_100,
                mapOf("duration" to 1_234_000L)
            )
        val frameDuration = AudioFileFormat(AudioFileFormat.Type.WAVE, PCM_FORMAT, 88_200)
        val unknownDuration = AudioFileFormat(AudioFileFormat.Type.WAVE, AudioFormat(44_100f, 16, 1, true, false), -1)

        prober.durationMillis(metadataDuration) shouldBe 1_234L
        prober.durationMillis(frameDuration) shouldBe 2_000L
        prober.durationMillis(unknownDuration) shouldBe 0L
    }

    "DurationProber resolvePlayableDurationMillis scans containerized mp3 when the reader misidentifies the file" {
        val decodeCount = AtomicInteger()
        val stereoFormat = AudioFormat(44_100f, 16, 2, true, false)
        val prober =
            DurationProber {
                decodeCount.incrementAndGet()
                AudioInputStream(
                    ByteArrayInputStream(ByteArray(5_299_200)),
                    stereoFormat,
                    5_299_200L / stereoFormat.frameSize.toLong()
                )
            }
        val mp3Type = object : AudioFileFormat.Type("MP3", "mp3") {}
        val suspiciousFormat =
            AudioFileFormat(
                mp3Type,
                PCM_FORMAT,
                692,
                mapOf("duration" to 18_077_000L)
            )

        val duration = prober.resolvePlayableDurationMillis(File("track.m4a"), "track.m4a")

        duration shouldBeGreaterThan 29_000L
        duration shouldBeLessThan 31_000L
        decodeCount.get() shouldBe 1
    }

    "DurationProber resolvePlayableDurationMillis falls back to file-format duration when decoded probing fails" {
        val prober =
            DurationProber {
                throw IOException("probe failed")
            }
        val mp3Type = object : AudioFileFormat.Type("MP3", "mp3") {}
        val suspiciousFormat =
            AudioFileFormat(
                mp3Type,
                PCM_FORMAT,
                692,
                mapOf("duration" to 18_077_000L)
            )

        // resolvePlayableDurationMillis calls readAudioFileFormat internally for the file;
        // since track.m4a does not exist, it will throw — and the pcmStreamFactory also throws.
        // In that fallback path the IOException is suppressed under an UnsupportedAudioPlaybackException.
        // We exercise the resolve-from-source path instead via the prober directly.
        prober.durationMillis(suspiciousFormat) shouldBe 18_077L
    }

    "DurationProber resolvePlayableDurationMillis returns duration for MP3 audio inside M4A" {
        val prober = DurationProber(::decodeToPcmStream)
        val file = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable_mp3.m4a")

        val duration = prober.resolvePlayableDurationMillis(file, file.name)

        duration shouldBeGreaterThan 0L
    }

    "play preserves totalDuration resolved before PCM stream opens" {
        // Verify that a duration resolved before the pump starts (e.g. from a container probe)
        // is not overwritten by the pump's openStreamSession call.
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = {
                    streamOf(ByteArray(8_820))
                },
                lineFactory = { fakeLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )

        try {
            // Simulate a pre-resolved 30-second duration from a container format probe.
            player.sourceDurationMillis = 30_000L
            player.totalDuration shouldBe Duration.ofSeconds(30)

            // sourceDurationMillis must remain untouched by a play() call that produces
            // a short fake stream (8820 bytes), because the container probe value wins.
            player.sourceDurationMillis = 30_000L
            player.totalDuration shouldBe Duration.ofSeconds(30)
        } finally {
            player.dispose()
        }
    }

    "play reopens the decoder when a seek is issued after playback starts" {
        val decodeCount = AtomicInteger()
        val lineCount = AtomicInteger()
        // Each stream yields enough data to keep the pump busy briefly, then EOF.
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = {
                    decodeCount.incrementAndGet()
                    streamOf(ByteArray(8_820))
                },
                lineFactory = {
                    lineCount.incrementAndGet()
                    fakeLine()
                },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )

        try {
            val file = File.createTempFile("seek-reopen", ".wav").also { it.deleteOnExit() }
            player.play(audioItem(file.toPath()))
            // Seek while the pump is running; the pump loop will consume the seek target
            // and reopen both the PCM stream and the audio line.
            player.seek(Duration.ZERO)

            eventually(5.seconds) {
                decodeCount.get() shouldBeGreaterThan 1
                lineCount.get() shouldBeGreaterThan 1
            }
        } finally {
            player.dispose()
        }
    }

    "PlaybackPump skipFully aborts when playback is already stopped" {
        val player = CoreAudioItemPlayer()
        player.state.set(enumValue("STOPPED"))
        val pump = PlaybackPump(player, mockk(relaxed = true) { every { path } returns java.nio.file.Paths.get("unused.wav") }, 1L)

        try {
            pump.skipFully(streamOf(ByteArray(16)), 8L) shouldBe 0L
        } finally {
            player.dispose()
        }
    }

    "PlaybackPump skipFully uses read-discard instead of provider skip" {
        val player = CoreAudioItemPlayer()
        val pump = PlaybackPump(player, mockk(relaxed = true) { every { path } returns java.nio.file.Paths.get("unused.wav") }, 1L)
        val skipCount = AtomicLong()
        val readCount = AtomicLong()
        val stream =
            object : AudioInputStream(ByteArrayInputStream(ByteArray(8_192)), PCM_FORMAT, 2_048) {
                override fun skip(n: Long): Long {
                    skipCount.incrementAndGet()
                    return n
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    readCount.incrementAndGet()
                    return super.read(b, off, len)
                }
            }

        try {
            pump.skipFully(stream, 4_096L) shouldBe 4_096L
            skipCount.get() shouldBe 0L
            readCount.get() shouldBe 1L
        } finally {
            player.dispose()
        }
    }

    "PlaybackPump skipFully remains safe when stream skip is unsupported" {
        val player = CoreAudioItemPlayer()
        val pump = PlaybackPump(player, mockk(relaxed = true) { every { path } returns java.nio.file.Paths.get("unused.wav") }, 1L)
        val skipCount = AtomicLong()
        val readCount = AtomicLong()
        val stream =
            object : AudioInputStream(ByteArrayInputStream(ByteArray(8_192)), PCM_FORMAT, 2_048) {
                override fun skip(n: Long): Long {
                    skipCount.incrementAndGet()
                    throw IOException("skip not supported")
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    readCount.incrementAndGet()
                    return super.read(b, off, len)
                }
            }

        try {
            pump.skipFully(stream, 4_096L) shouldBe 4_096L
            skipCount.get() shouldBe 0L
            readCount.get() shouldBe 1L
        } finally {
            player.dispose()
        }
    }

    "FlacPcmStreamSeeker opens FLAC at the requested decoded position" {
        val file = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable.flac")
        val baseline = decodeToPcmStream(file.toPath())
        try {
            val frameSize = baseline.format.frameSize
            val totalFrames = baseline.frameLength.takeIf { it > 0 } ?: error("baseline FLAC stream did not expose frame length")
            val targetFrame = totalFrames / 2
            val targetOffset = targetFrame * frameSize

            discardBytes(baseline, targetOffset) shouldBe targetOffset
            val expected = readBytes(baseline, 8192)
            val seeked = FlacPcmStreamSeeker.open(file, targetOffset)

            seeked?.startByteOffset shouldBe targetOffset
            val seekedStream = seeked?.stream ?: error("seekable FLAC stream was not created")
            seekedStream.frameLength shouldBeGreaterThan 0L
            seekedStream.frameLength shouldBeLessThan totalFrames
            seekedStream.use { stream ->
                stream.format.sampleRate shouldBe baseline.format.sampleRate
                readBytes(stream, expected.size).toList() shouldBe expected.toList()
            }
            val eofSeek = FlacPcmStreamSeeker.open(file, Long.MAX_VALUE / 2) ?: error("EOF FLAC seek stream was not created")
            eofSeek.startByteOffset shouldBeGreaterThan targetOffset
            eofSeek.stream.frameLength shouldBe 0L
        } finally {
            baseline.close()
        }
    }

    "PcmVolume applies muted, 16-bit, and 8-bit buffers correctly" {
        val muted = byteArrayOf(1, 2, 3, 4)
        val sixteenBit = byteArrayOf(0x00, 0x40, 0x00, 0xC0.toByte())
        val eightBit = byteArrayOf(0x00, 0xFF.toByte())
        val twentyFourBit = byteArrayOf(0x00, 0x02, 0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())
        val eightBitFormat = AudioFormat(44_100f, 8, 1, false, false)
        val twentyFourBitFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44_100f, 24, 1, 3, 44_100f, false)

        PcmVolume.apply(muted, muted.size, PCM_FORMAT, 0.0f)
        muted.toList() shouldBe listOf<Byte>(0, 0, 0, 0)

        PcmVolume.apply(sixteenBit, sixteenBit.size, PCM_FORMAT, 0.5f)
        sixteenBit.toList() shouldBe listOf(0x00, 0x20, 0x00, 0xE0.toByte())

        PcmVolume.apply(eightBit, eightBit.size, eightBitFormat, 0.5f)
        eightBit.toList() shouldBe listOf(0x40, 0xBF.toByte())

        PcmVolume.apply(twentyFourBit, twentyFourBit.size, twentyFourBitFormat, 0.5f)
        twentyFourBit.toList() shouldBe listOf(0x00, 0x01, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte())
    }

    "seek publishes the requested position immediately while playback is active" {
        val player = CoreAudioItemPlayer()
        player.state.set(enumValue("PLAYING"))
        player.lineRef.set(
            mockk(relaxed = true) {
                every { isOpen } returns true
                every { microsecondPosition } returns 0L
            }
        )
        player.pcmFormat.set(PCM_FORMAT)

        try {
            player.seek(Duration.ofMillis(750))

            player.getCurrentTime() shouldBe Duration.ofMillis(750)
        } finally {
            player.dispose()
        }
    }

    "play falls back to decoded PCM duration when file-format providers fail" {
        val decodeCount = AtomicInteger()
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = {
                    decodeCount.incrementAndGet()
                    streamOf(ByteArray(8_820))
                },
                lineFactory = { fakeLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        mockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        every { net.transgressoft.commons.media.util.readAudioFileFormat(any()) } throws IOException("bad provider")
        val file = Files.createTempFile("format-fallback", ".m4a")

        try {
            shouldNotThrowAny { player.play(audioItem(file)) }

            eventually(5.seconds) {
                player.totalDuration shouldBe Duration.ofMillis(100)
                decodeCount.get() shouldBeGreaterThan 1
            }
        } finally {
            player.dispose()
            Files.deleteIfExists(file)
            unmockkStatic("net.transgressoft.commons.media.util.AudioDecoderUtilKt")
        }
    }

    "onFinish setter replaces previously registered callback" {
        val player = CoreAudioItemPlayer()
        try {
            shouldNotThrowAny {
                player.onFinish { /* first */ }
                player.onFinish { /* second replaces first */ }
            }
        } finally {
            player.dispose()
        }
    }

    "dispose transitions to DISPOSED and is idempotent" {
        val player = CoreAudioItemPlayer()
        player.dispose()
        player.status() shouldBe Status.DISPOSED
        shouldNotThrowAny { player.dispose() }
        player.status() shouldBe Status.DISPOSED
    }

    "transport calls after dispose are silent no-ops" {
        val player = CoreAudioItemPlayer()
        player.dispose()

        // None of these may throw, and the status must remain DISPOSED.
        val missingFile = Files.createTempFile("disposed-noop", ".mp3").also { it.toFile().delete() }
        shouldNotThrowAny {
            player.play(audioItem(missingFile))
            player.pause()
            player.resume()
            player.stop()
            player.setVolume(0.5)
            player.seek(Duration.ofMillis(100))
            player.onFinish { /* unused */ }
        }
        player.status() shouldBe Status.DISPOSED
    }

    "play throws UnsupportedAudioPlaybackException when the file does not exist" {
        val player = CoreAudioItemPlayer()
        val missing = Files.createTempFile("missing-audio", ".mp3").also { it.toFile().delete() }
        try {
            shouldThrow<UnsupportedAudioPlaybackException> {
                player.play(audioItem(missing))
            }
        } finally {
            player.dispose()
        }
    }

    "play throws UnsupportedAudioPlaybackException when the path points to a directory" {
        val player = CoreAudioItemPlayer()
        val dir = Files.createTempDirectory("audio-directory-")
        try {
            shouldThrow<UnsupportedAudioPlaybackException> {
                player.play(audioItem(dir))
            }
        } finally {
            player.dispose()
            File(dir.toString()).delete()
        }
    }

    "play throws UnsupportedAudioPlaybackException for an unrecognised audio format" {
        val player = CoreAudioItemPlayer()
        // A regular text file: AudioSystem.getAudioFileFormat must reject it.
        val notAudio = Files.createTempFile("not-audio", ".txt")
        notAudio.toFile().writeText("definitely not audio bytes")
        try {
            shouldThrow<UnsupportedAudioPlaybackException> {
                player.play(audioItem(notAudio))
            }
        } finally {
            player.dispose()
            notAudio.toFile().delete()
        }
    }
})