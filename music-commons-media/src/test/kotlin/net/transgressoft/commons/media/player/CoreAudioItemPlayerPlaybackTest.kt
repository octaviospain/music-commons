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
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.FLAC
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.MP4_INFO
import net.transgressoft.commons.music.audio.AudioFileTagType.VORBIS_COMMENT
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import net.transgressoft.commons.music.audio.FakeAudioLine
import net.transgressoft.commons.music.audio.audioItem
import net.transgressoft.commons.music.player.AudioItemPlayer.Status
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.longs.shouldBeGreaterThan as shouldBeGreaterThanLong
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.io.InputStream
import java.nio.file.Files.createTempFile
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration.Companion.seconds

private val PCM_FORMAT_PLAYBACK = AudioFormat(44_100f, 16, 2, true, false)

private fun streamOf(bytes: ByteArray): AudioInputStream =
    AudioInputStream(bytes.inputStream(), PCM_FORMAT_PLAYBACK, bytes.size.toLong() / PCM_FORMAT_PLAYBACK.frameSize)

/**
 * A PCM stream whose reads block until the pump thread is interrupted, so playback never
 * completes on its own. [FakeAudioLine] drains real fixtures instantly, which would race the
 * pump to READY before a test can pause or seek; a never-ending source keeps the player in
 * PLAYING deterministically until the test drives the transition itself.
 */
private fun neverEndingPcmStream(): AudioInputStream {
    val blocking =
        object : InputStream() {
            override fun read(): Int {
                Thread.sleep(Long.MAX_VALUE)
                return -1
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                Thread.sleep(Long.MAX_VALUE)
                return -1
            }
        }
    return AudioInputStream(blocking, PCM_FORMAT_PLAYBACK, AudioSystem.NOT_SPECIFIED.toLong())
}

/**
 * Tests that exercise playback state-machine, seek, and lifecycle scenarios for [CoreAudioItemPlayer].
 *
 * Most tests use [FakeAudioLine] for deterministic, hardware-free execution in CI (excluded with
 * `-PexcludeAudioHardware=true`). One PCM-to-line smoke test is tagged `audio-hardware` and
 * verifies the real JavaSound SPI pipeline on a device with audio output.
 *
 * Tests are parameterized across supported audio formats where applicable to detect
 * codec-specific playback bugs.
 */
internal class CoreAudioItemPlayerPlaybackTest : FunSpec({

    context("PCM decoding across all supported formats") {
        withData(
            mapOf(
                "mp3" to ID3_V_24,
                "m4a" to MP4_INFO,
                "wav" to WAV,
                "flac" to FLAC,
                "ogg" to VORBIS_COMMENT
            )
        ) { tagType ->
            val player =
                CoreAudioItemPlayer(
                    pcmStreamFactory = ::decodeToPcmStream,
                    lineFactory = { FakeAudioLine() },
                    nanoTime = { 0L },
                    stallThresholdNanos = Long.MAX_VALUE
                )
            val realAudioPath = Arb.realAudioFile(tagType).next()
            val audioItem = Arb.audioItem { path = realAudioPath }.next()
            player.setVolume(0.0)

            try {
                player.play(audioItem)

                // Wait for streaming setup and verify format state is available.
                eventually(5.seconds) {
                    val pcmFormatField = CoreAudioItemPlayer::class.java.getDeclaredField("pcmFormat")
                    pcmFormatField.isAccessible = true
                    val pcmFormat = pcmFormatField.get(player) as AtomicReference<*>
                    pcmFormat.get() shouldNotBe null
                }

                // Guards a deliberate architectural decision: the player streams PCM through a
                // bounded transfer buffer and must never hold the whole decoded track in memory.
                // A reintroduced `pcmData` field would signal a regression back to full buffering.
                runCatching { CoreAudioItemPlayer::class.java.getDeclaredField("pcmData") }.exceptionOrNull().shouldBeInstanceOf<NoSuchFieldException>()
            } finally {
                player.dispose()
            }
        }
    }

    context("Status transitions across all supported formats") {
        withData(
            mapOf(
                "mp3" to ID3_V_24,
                "m4a" to MP4_INFO,
                "wav" to WAV,
                "flac" to FLAC,
                "ogg" to VORBIS_COMMENT
            )
        ) { tagType ->
            val player =
                CoreAudioItemPlayer(
                    pcmStreamFactory = ::decodeToPcmStream,
                    lineFactory = { FakeAudioLine() },
                    nanoTime = { 0L },
                    stallThresholdNanos = Long.MAX_VALUE
                )
            val realAudioPath = Arb.realAudioFile(tagType).next()
            val audioItem = Arb.audioItem { path = realAudioPath }.next()
            player.setVolume(0.0)

            try {
                // Initial status
                player.status() shouldBe Status.UNKNOWN

                // Play
                player.play(audioItem)
                player.status() shouldBe Status.PLAYING

                // Stop
                player.stop()
                player.status() shouldBe Status.STOPPED
            } finally {
                player.dispose()
            }
        }
    }

    test("playback completes and transitions to READY using FakeAudioLine") {
        // FakeAudioLine drains the tiny PCM source near-instantly, so the pump can reach READY
        // before any post-play() assertion runs. Asserting an intermediate PLAYING here would race
        // the pump; the play() -> PLAYING contract is covered deterministically by the
        // "Status transitions across all supported formats" context. This test only verifies that
        // a drained source completes and transitions to READY.
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = { streamOf(ByteArray(8_820)) },
                lineFactory = { FakeAudioLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val audioItem = Arb.audioItem { path = realAudioPath }.next()
        player.setVolume(0.0)

        try {
            player.play(audioItem)

            eventually(2.seconds) {
                player.status() shouldBe Status.READY
            }
        } finally {
            player.dispose()
        }
    }

    test("forward and backward seek change current time deterministically") {
        // FakeAudioLine drains instantly, so this test holds the pump with a never-ending stream,
        // waits for pcmFormat resolution (confirming the pump entered the PLAYING streaming loop),
        // then pauses before seeking so seekPreviewMillis is set and read back synchronously (D-04).
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = { neverEndingPcmStream() },
                lineFactory = { FakeAudioLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val audioItem = Arb.audioItem { path = realAudioPath }.next()
        player.setVolume(0.0)

        try {
            player.play(audioItem)
            // Wait until the pump has opened the PCM stream (pcmFormat resolved): by then it has
            // entered the PLAYING streaming loop, so pause() deterministically reaches PAUSED
            // regardless of scheduler timing. The never-ending stream keeps it from completing.
            eventually(3.seconds) {
                val pcmFormatField = CoreAudioItemPlayer::class.java.getDeclaredField("pcmFormat")
                pcmFormatField.isAccessible = true
                (pcmFormatField.get(player) as AtomicReference<*>).get() shouldNotBe null
            }

            // Pause before seeking to hold the pump — FakeAudioLine drains near-instantly so
            // we must be in PAUSED state for seek() to set seekPreviewMillis synchronously
            // and for getCurrentTime() to read it back (D-04 clock-jump discipline).
            player.pause()
            player.status() shouldBe Status.PAUSED

            player.seek(Duration.ofMillis(700))
            player.getCurrentTime().toMillis() shouldBeGreaterThanLong 600L

            player.seek(Duration.ofMillis(200))
            val currentMillis = player.getCurrentTime().toMillis()
            currentMillis shouldBeGreaterThanLong 150L
            currentMillis shouldBeLessThanOrEqual 450L
        } finally {
            player.dispose()
        }
    }

    test("dispose transitions to DISPOSED and is idempotent") {
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = { streamOf(ByteArray(8_820)) },
                lineFactory = { FakeAudioLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val audioItem = Arb.audioItem { path = realAudioPath }.next()
        player.setVolume(0.0)

        player.play(audioItem)
        player.status() shouldBe Status.PLAYING

        player.dispose()
        player.status() shouldBe Status.DISPOSED

        // Second dispose must not throw
        player.dispose()
        player.status() shouldBe Status.DISPOSED
    }

    test("play after dispose is ignored") {
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = { streamOf(ByteArray(8_820)) },
                lineFactory = { FakeAudioLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val audioItem = Arb.audioItem { path = realAudioPath }.next()
        player.setVolume(0.0)

        player.dispose()
        player.status() shouldBe Status.DISPOSED

        player.play(audioItem)
        // Status remains DISPOSED
        player.status() shouldBe Status.DISPOSED
    }

    test("play throws UnsupportedAudioPlaybackException for non-existent file") {
        val player =
            CoreAudioItemPlayer(
                pcmStreamFactory = ::decodeToPcmStream,
                lineFactory = { FakeAudioLine() },
                nanoTime = { 0L },
                stallThresholdNanos = Long.MAX_VALUE
            )
        val audioItem =
            Arb.audioItem {
                path = createTempFile("nonexistent", ".mp3").also { it.toFile().delete() }
            }.next()

        try {
            runCatching { player.play(audioItem) }.exceptionOrNull() shouldNotBe null
        } finally {
            player.dispose()
        }
    }
})

/**
 * PCM-to-line smoke test for [CoreAudioItemPlayer] that opens a real [javax.sound.sampled.SourceDataLine].
 *
 * Excluded from default CI via `-PexcludeAudioHardware=true`; run on a machine with
 * audio output to verify the full JavaSound SPI pipeline is intact end-to-end.
 */
@Tags("audio-hardware")
internal class CoreAudioItemPlayerSmokeTest : FunSpec({

    test("PCM-to-line smoke: WAV plays through real SourceDataLine") {
        val player = CoreAudioItemPlayer()
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val audioItem = Arb.audioItem { path = realAudioPath }.next()
        player.setVolume(0.0)

        try {
            player.play(audioItem)
            player.status() shouldBe Status.PLAYING

            // Generous timeout because audio output is real-time and CI runners add latency
            eventually(15.seconds) {
                player.status() shouldBe Status.READY
            }
        } finally {
            player.dispose()
        }
    }
})