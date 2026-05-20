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

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer.Status
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.lang.reflect.Field
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.SourceDataLine

private val PCM_FORMAT = AudioFormat(44_100f, 16, 1, true, false)

@Suppress("UNCHECKED_CAST")
private fun enumValue(name: String): Any {
    val enumClass = CoreAudioItemPlayer::class.java.declaredClasses.single { it.simpleName == "InternalState" }
    return java.lang.Enum.valueOf(enumClass as Class<out Enum<*>>, name)
}

/**
 * Unit tests for [CoreAudioItemPlayer] that exercise the public API without triggering
 * real audio output. Complements [CoreAudioItemPlayerPlaybackTest], which requires a
 * working audio device and is gated behind the `requires-playback` tag.
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

    fun field(name: String): Field =
        CoreAudioItemPlayer::class.java.getDeclaredField(name).apply {
            isAccessible = true
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
        val state = field("state").get(player) as AtomicReference<*>
        @Suppress("UNCHECKED_CAST")
        (state as AtomicReference<Any>).set(enumValue("PAUSED"))

        try {
            player.seek(Duration.ofMillis(100))
            player.seek(Duration.ofMillis(250))

            decodeCount.get() shouldBe 0
            (field("seekTargetMillis").get(player) as AtomicLong).get() shouldBe 250L
        } finally {
            player.dispose()
        }
    }

    "seek target consumption returns the latest target once" {
        val player = CoreAudioItemPlayer()
        val consumeSeekTarget =
            CoreAudioItemPlayer::class.java.getDeclaredMethod("consumeSeekTarget").apply {
                isAccessible = true
            }

        try {
            player.seek(Duration.ofMillis(100))
            player.seek(Duration.ofMillis(250))

            consumeSeekTarget.invoke(player) shouldBe 250L
            consumeSeekTarget.invoke(player) shouldBe -1L
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

    "sustained decoder starvation transitions from STALLED back to PLAYING" {
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = { streamOf(ByteArray(8)) },
                lineFactory = { fakeLine() },
                nanoTime = { 0L },
                stallThresholdNanos = 100_000_000L
            )
        val state = field("state").get(player) as AtomicReference<*>
        @Suppress("UNCHECKED_CAST")
        (state as AtomicReference<Any>).set(enumValue("PLAYING"))
        val updateStallStatus =
            CoreAudioItemPlayer::class.java.getDeclaredMethod("updateStallStatus", Long::class.javaPrimitiveType).apply {
                isAccessible = true
            }

        try {
            updateStallStatus.invoke(player, 200_000_000L)
            player.status() shouldBe Status.STALLED

            updateStallStatus.invoke(player, 1L)
            player.status() shouldBe Status.PLAYING
        } finally {
            player.dispose()
        }
    }

    "stalled playback recovers to PAUSED while transport is paused" {
        val player = CoreAudioItemPlayer()
        val state = field("state").get(player) as AtomicReference<*>
        val internalStatus = field("internalStatus").get(player) as AtomicReference<*>
        @Suppress("UNCHECKED_CAST")
        (state as AtomicReference<Any>).set(enumValue("PAUSED"))
        @Suppress("UNCHECKED_CAST")
        (internalStatus as AtomicReference<Status>).set(Status.STALLED)
        val recoverFromStallIfNeeded =
            CoreAudioItemPlayer::class.java.getDeclaredMethod("recoverFromStallIfNeeded").apply {
                isAccessible = true
            }

        try {
            recoverFromStallIfNeeded.invoke(player)

            player.status() shouldBe Status.PAUSED
        } finally {
            player.dispose()
        }
    }

    "duration prefers metadata and falls back to frame length" {
        val player = CoreAudioItemPlayer()
        val durationMillis =
            CoreAudioItemPlayer::class.java.getDeclaredMethod("durationMillis", AudioFileFormat::class.java).apply {
                isAccessible = true
            }
        val metadataDuration =
            AudioFileFormat(
                AudioFileFormat.Type.WAVE,
                PCM_FORMAT,
                44_100,
                mapOf("duration" to 1_234_000L)
            )
        val frameDuration = AudioFileFormat(AudioFileFormat.Type.WAVE, PCM_FORMAT, 88_200)
        val unknownDuration = AudioFileFormat(AudioFileFormat.Type.WAVE, AudioFormat(44_100f, 16, 1, true, false), -1)

        try {
            durationMillis.invoke(player, metadataDuration) shouldBe 1_234L
            durationMillis.invoke(player, frameDuration) shouldBe 2_000L
            durationMillis.invoke(player, unknownDuration) shouldBe 0L
        } finally {
            player.dispose()
        }
    }

    "resolveSourceDurationMillis scans containerized mp3 when the reader misidentifies the file" {
        val decodeCount = AtomicInteger()
        val stereoFormat = AudioFormat(44_100f, 16, 2, true, false)
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = {
                    decodeCount.incrementAndGet()
                    AudioInputStream(
                        ByteArrayInputStream(ByteArray(5_299_200)),
                        stereoFormat,
                        5_299_200L / stereoFormat.frameSize.toLong()
                    )
                },
                lineFactory = { fakeLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        val resolveSourceDurationMillis =
            CoreAudioItemPlayer::class.java.getDeclaredMethod("resolveSourceDurationMillis", File::class.java, AudioFileFormat::class.java).apply {
                isAccessible = true
            }
        val mp3Type = object : AudioFileFormat.Type("MP3", "mp3") {}
        val suspiciousFormat =
            AudioFileFormat(
                mp3Type,
                PCM_FORMAT,
                692,
                mapOf("duration" to 18_077_000L)
            )

        try {
            val duration =
                resolveSourceDurationMillis.invoke(player, File("track.m4a"), suspiciousFormat) as Long

            duration shouldBeGreaterThan 29_000L
            duration shouldBeLessThan 31_000L
            decodeCount.get() shouldBe 1
        } finally {
            player.dispose()
        }
    }

    "resolveSourceDurationMillis falls back to file-format duration when decoded probing fails" {
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = {
                    throw IOException("probe failed")
                },
                lineFactory = { fakeLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        val resolveSourceDurationMillis =
            CoreAudioItemPlayer::class.java.getDeclaredMethod("resolveSourceDurationMillis", File::class.java, AudioFileFormat::class.java).apply {
                isAccessible = true
            }
        val mp3Type = object : AudioFileFormat.Type("MP3", "mp3") {}
        val suspiciousFormat =
            AudioFileFormat(
                mp3Type,
                PCM_FORMAT,
                692,
                mapOf("duration" to 18_077_000L)
            )

        try {
            val duration =
                resolveSourceDurationMillis.invoke(player, File("track.m4a"), suspiciousFormat) as Long

            duration shouldBe 18_077L
        } finally {
            player.dispose()
        }
    }

    "openStreamSession refreshes totalDuration from the decoded PCM stream" {
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = {
                    streamOf(ByteArray(88_200))
                },
                lineFactory = { fakeLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        val openStreamSession =
            CoreAudioItemPlayer::class.java.getDeclaredMethod("openStreamSession", File::class.java, Long::class.javaPrimitiveType).apply {
                isAccessible = true
            }

        try {
            val session = openStreamSession.invoke(player, File("unused.m4a"), 0L) as Any
            val stream = session.javaClass.getDeclaredMethod("getStream").apply { isAccessible = true }.invoke(session) as AudioInputStream
            try {
                player.totalDuration shouldBe Duration.ofSeconds(1)
            } finally {
                stream.close()
            }
        } finally {
            player.dispose()
        }
    }

    "streaming reopens the decoder when a pending seek is consumed" {
        val decodeCount = AtomicInteger()
        val lineCount = AtomicInteger()
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = {
                    decodeCount.incrementAndGet()
                    streamOf(ByteArray(16))
                },
                lineFactory = {
                    lineCount.incrementAndGet()
                    fakeLine()
                },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        val state = field("state").get(player) as AtomicReference<*>
        @Suppress("UNCHECKED_CAST")
        (state as AtomicReference<Any>).set(enumValue("PLAYING"))
        val streamPcm =
            CoreAudioItemPlayer::class.java.getDeclaredMethod("streamPcm", File::class.java).apply {
                isAccessible = true
            }

        try {
            player.seek(Duration.ZERO)
            streamPcm.invoke(player, File("unused.wav"))

            decodeCount.get() shouldBe 2
            lineCount.get() shouldBe 2
        } finally {
            player.dispose()
        }
    }

    "skipFully aborts when playback is already stopped" {
        val player = CoreAudioItemPlayer()
        val state = field("state").get(player) as AtomicReference<*>
        @Suppress("UNCHECKED_CAST")
        (state as AtomicReference<Any>).set(enumValue("STOPPED"))
        val skipFully =
            CoreAudioItemPlayer::class.java.getDeclaredMethod("skipFully", AudioInputStream::class.java, Long::class.javaPrimitiveType).apply {
                isAccessible = true
            }

        try {
            skipFully.invoke(player, streamOf(ByteArray(16)), 8L) shouldBe 0L
        } finally {
            player.dispose()
        }
    }

    "volume scaling handles muted, 16-bit, and 8-bit buffers" {
        val player = CoreAudioItemPlayer()
        val applyVolume =
            CoreAudioItemPlayer::class.java
                .getDeclaredMethod("applyVolume", ByteArray::class.java, Int::class.javaPrimitiveType, AudioFormat::class.java)
                .apply {
                    isAccessible = true
                }
        val volume = field("volume")
        val muted = byteArrayOf(1, 2, 3, 4)
        val sixteenBit = byteArrayOf(0x00, 0x40, 0x00, 0xC0.toByte())
        val eightBit = byteArrayOf(0x00, 0xFF.toByte())
        val eightBitFormat = AudioFormat(44_100f, 8, 1, false, false)

        try {
            volume.setDouble(player, 0.0)
            applyVolume.invoke(player, muted, muted.size, PCM_FORMAT)
            muted.toList() shouldBe listOf<Byte>(0, 0, 0, 0)

            volume.setDouble(player, 0.5)
            applyVolume.invoke(player, sixteenBit, sixteenBit.size, PCM_FORMAT)
            sixteenBit.toList() shouldBe listOf<Byte>(0x00, 0x20, 0x00, 0xE0.toByte())

            applyVolume.invoke(player, eightBit, eightBit.size, eightBitFormat)
            eightBit.toList() shouldBe listOf<Byte>(0x40, 0xBF.toByte())
        } finally {
            player.dispose()
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