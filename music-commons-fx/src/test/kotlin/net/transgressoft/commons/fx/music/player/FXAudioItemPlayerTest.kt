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

package net.transgressoft.commons.fx.music.player

import net.transgressoft.commons.media.player.CoreAudioItemPlayer
import net.transgressoft.commons.music.audio.AudioFileCodec
import net.transgressoft.commons.music.audio.AudioFileTagType.FLAC
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javafx.util.Duration as FxDuration
import org.testfx.api.FxToolkit
import java.nio.file.Files
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [FXAudioItemPlayer] FX observable property behavior and delegation.
 *
 * Uses mocked [CoreAudioItemPlayer] to avoid heavy playback operations while verifying
 * that FX properties (volume, status, currentTime) correctly reflect core player state.
 *
 * Class-level [Tags] of `linux-only` means these tests are skipped when
 * `-PexcludeLinuxOnly=true` is set.
 */
internal class FXAudioItemPlayerTest : FunSpec({
    isolationMode = IsolationMode.SingleInstance

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    context("FX observable properties reflect core player state") {

        test("initial properties are at default values") {
            val corePlayer =
                mockk<CoreAudioItemPlayer>(relaxed = true) {
                    every { status() } returns AudioItemPlayer.Status.UNKNOWN
                    every { getCurrentTime() } returns Duration.ZERO
                    every { totalDuration } returns Duration.ZERO
                }
            val player = FXAudioItemPlayer(corePlayer)

            try {
                player.statusProperty.get() shouldBe AudioItemPlayer.Status.UNKNOWN
                player.currentTimeProperty.get() shouldBe FxDuration.ZERO
                player.volumeProperty.get() shouldBe 1.0
            } finally {
                player.dispose()
            }
        }

        test("play delegates to core player and syncs status") {
            val audioItem =
                mockk<ReactiveAudioItem<*>>(relaxed = true) {
                    every { path } returns Files.createTempFile("test", ".wav")
                    every { fileName } returns "test.wav"
                    every { extension } returns "wav"
                    every { encoding } returns null
                    every { encoder } returns null
                }
            val corePlayer =
                mockk<CoreAudioItemPlayer>(relaxed = true) {
                    every { status() } returns AudioItemPlayer.Status.PLAYING
                    every { getCurrentTime() } returns Duration.ofMillis(100)
                    every { totalDuration } returns Duration.ofSeconds(10)
                }
            val player = FXAudioItemPlayer(corePlayer)

            try {
                player.play(audioItem)

                verify { corePlayer.play(audioItem) }

                eventually(1.seconds) {
                    player.statusProperty.get() shouldBe AudioItemPlayer.Status.PLAYING
                    player.currentTimeProperty.get().toMillis() shouldBe 100.0
                }
            } finally {
                player.dispose()
                audioItem.path.toFile().delete()
            }
        }

        test("pause delegates to core player and syncs status") {
            val corePlayer =
                mockk<CoreAudioItemPlayer>(relaxed = true) {
                    every { status() } returns AudioItemPlayer.Status.PAUSED
                    every { getCurrentTime() } returns Duration.ofMillis(500)
                }
            val player = FXAudioItemPlayer(corePlayer)

            try {
                player.pause()

                verify { corePlayer.pause() }

                eventually(1.seconds) {
                    player.statusProperty.get() shouldBe AudioItemPlayer.Status.PAUSED
                    player.currentTimeProperty.get().toMillis() shouldBe 500.0
                }
            } finally {
                player.dispose()
            }
        }

        test("stop delegates to core player and syncs status") {
            val corePlayer =
                mockk<CoreAudioItemPlayer>(relaxed = true) {
                    every { status() } returns AudioItemPlayer.Status.STOPPED
                    every { getCurrentTime() } returns Duration.ZERO
                }
            val player = FXAudioItemPlayer(corePlayer)

            try {
                player.stop()

                verify { corePlayer.stop() }

                eventually(1.seconds) {
                    player.statusProperty.get() shouldBe AudioItemPlayer.Status.STOPPED
                    player.currentTimeProperty.get() shouldBe FxDuration.ZERO
                }
            } finally {
                player.dispose()
            }
        }

        test("resume delegates to core player and syncs status") {
            val corePlayer =
                mockk<CoreAudioItemPlayer>(relaxed = true) {
                    every { status() } returns AudioItemPlayer.Status.PLAYING
                    every { getCurrentTime() } returns Duration.ofMillis(750)
                }
            val player = FXAudioItemPlayer(corePlayer)

            try {
                player.resume()

                verify { corePlayer.resume() }

                eventually(1.seconds) {
                    player.statusProperty.get() shouldBe AudioItemPlayer.Status.PLAYING
                    player.currentTimeProperty.get().toMillis() shouldBe 750.0
                }
            } finally {
                player.dispose()
            }
        }

        test("setVolume updates FX property and delegates to core player") {
            val corePlayer = mockk<CoreAudioItemPlayer>(relaxed = true)
            val player = FXAudioItemPlayer(corePlayer)

            try {
                player.setVolume(0.75)

                eventually(1.seconds) {
                    player.volumeProperty.get() shouldBe 0.75
                }

                verify { corePlayer.setVolume(0.75) }
            } finally {
                player.dispose()
            }
        }

        test("seek delegates to core player") {
            val corePlayer = mockk<CoreAudioItemPlayer>(relaxed = true)
            val player = FXAudioItemPlayer(corePlayer)
            val seekPosition = Duration.ofSeconds(30)

            try {
                player.seek(seekPosition)

                verify { corePlayer.seek(seekPosition) }
            } finally {
                player.dispose()
            }
        }

        test("dispose stops progress ticker and delegates to core player") {
            val corePlayer = mockk<CoreAudioItemPlayer>(relaxed = true)
            val player = FXAudioItemPlayer(corePlayer)

            player.dispose()

            verify { corePlayer.dispose() }

            // Subsequent dispose should not throw (idempotent)
            player.dispose()
        }

        test("play throws UnsupportedAudioPlaybackException for non-existent file") {
            // Use real CoreAudioItemPlayer to test actual file validation
            val player = FXAudioItemPlayer()
            val nonExistentFile = Files.createTempFile("corrupt-audio", ".mp3").also { it.toFile().delete() }
            val item =
                mockk<ReactiveAudioItem<*>>(relaxed = true) {
                    every { extension } returns "mp3"
                    every { encoding } returns null
                    every { encoder } returns null
                    every { fileName } returns "corrupt.mp3"
                    every { duration } returns Duration.ofSeconds(0)
                    every { path } returns nonExistentFile
                }

            try {
                shouldThrow<UnsupportedAudioPlaybackException> {
                    player.play(item)
                }
            } finally {
                player.dispose()
            }
        }
    }

    context("detectCodec delegates to AudioItemPlayer") {

        test("detects AAC codec for M4A files") {
            val m4a =
                mockk<ReactiveAudioItem<*>>(relaxed = true) {
                    every { extension } returns "m4a"
                    every { encoding } returns "AAC"
                    every { encoder } returns null
                }
            val codec = FXAudioItemPlayer.detectCodec(m4a)
            codec shouldBe AudioItemPlayer.detectCodec(m4a)
            codec shouldBe AudioFileCodec.AAC
        }

        test("detects primary codec for single-codec formats") {
            val mp3 =
                mockk<ReactiveAudioItem<*>>(relaxed = true) {
                    every { extension } returns "mp3"
                    every { encoding } returns "MPEG-1 Layer 3"
                    every { encoder } returns null
                }
            val wav =
                mockk<ReactiveAudioItem<*>>(relaxed = true) {
                    every { extension } returns "wav"
                    every { encoding } returns "PCM"
                    every { encoder } returns null
                }
            val flac =
                mockk<ReactiveAudioItem<*>>(relaxed = true) {
                    every { extension } returns "flac"
                    every { encoding } returns "FLAC"
                    every { encoder } returns null
                }
            val ogg =
                mockk<ReactiveAudioItem<*>>(relaxed = true) {
                    every { extension } returns "ogg"
                    every { encoding } returns "Vorbis"
                    every { encoder } returns null
                }

            AudioItemPlayer.detectCodec(mp3) shouldBe AudioFileCodec.MP3
            AudioItemPlayer.detectCodec(wav) shouldBe AudioFileCodec.PCM
            AudioItemPlayer.detectCodec(flac) shouldBe AudioFileCodec.FLAC
            AudioItemPlayer.detectCodec(ogg) shouldBe AudioFileCodec.VORBIS
        }
    }
})