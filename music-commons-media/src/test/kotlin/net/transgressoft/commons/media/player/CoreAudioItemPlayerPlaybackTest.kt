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

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.FLAC
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.MP4_INFO
import net.transgressoft.commons.music.audio.AudioFileTagType.VORBIS_COMMENT
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
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
import java.nio.file.Files.createTempFile
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Tests that exercise actual JavaSound SPI playback through [CoreAudioItemPlayer].
 *
 * Class-level [Tags] of `requires-playback` mean these tests are skipped when `-PexcludePlaybackTests=true` is set.
 *
 * Tests are parameterized across all supported audio formats (MP3, M4A, WAV, FLAC, OGG) to
 * detect codec-specific playback bugs.
 */
@Tags("requires-playback")
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
            val player = CoreAudioItemPlayer()
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
            val player = CoreAudioItemPlayer()
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

    test("WAV playback completes and transitions to READY") {
        val player = CoreAudioItemPlayer()
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val audioItem = Arb.audioItem { path = realAudioPath }.next()
        player.setVolume(0.0)

        try {
            player.play(audioItem)
            player.status() shouldBe Status.PLAYING

            // Wait for the WAV track (~9s) to finish playing back. The timeout is generous
            // because audio output is real-time and CI runners can add latency.
            eventually(15.seconds) {
                player.status() shouldBe Status.READY
            }
        } finally {
            player.dispose()
        }
    }

    test("forward and backward seek change current time for WAV") {
        val player = CoreAudioItemPlayer()
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val audioItem = Arb.audioItem { path = realAudioPath }.next()
        player.setVolume(0.0)

        try {
            player.play(audioItem)
            eventually(3.seconds) {
                player.status() shouldBe Status.PLAYING
            }

            player.seek(Duration.ofMillis(700))
            eventually(1.seconds) {
                player.getCurrentTime().toMillis() shouldBeGreaterThanLong 600L
            }

            player.seek(Duration.ofMillis(200))
            eventually(1.seconds) {
                val currentMillis = player.getCurrentTime().toMillis()
                currentMillis shouldBeGreaterThanLong 150L
                currentMillis shouldBeLessThanOrEqual 450L
            }
        } finally {
            player.dispose()
        }
    }

    test("dispose transitions to DISPOSED and is idempotent") {
        val player = CoreAudioItemPlayer()
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val audioItem = Arb.audioItem { path = realAudioPath }.next()
        player.setVolume(0.0)

        player.play(audioItem)
        player.status() shouldBe Status.PLAYING

        player.dispose()
        player.status() shouldBe Status.DISPOSED

        // Second dispose should not throw
        player.dispose()
        player.status() shouldBe Status.DISPOSED
    }

    test("play after dispose is ignored") {
        val player = CoreAudioItemPlayer()
        val realAudioPath = Arb.realAudioFile(WAV).next()
        val audioItem = Arb.audioItem { path = realAudioPath }.next()
        player.setVolume(0.0)

        player.dispose()
        player.status() shouldBe Status.DISPOSED

        player.play(audioItem)
        // Status should remain DISPOSED
        player.status() shouldBe Status.DISPOSED
    }

    test("play throws UnsupportedAudioPlaybackException for non-existent file") {
        val player = CoreAudioItemPlayer()
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