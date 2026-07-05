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
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
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
 */
internal class FXAudioItemPlayerTest : FunSpec({
    isolationMode = IsolationMode.SingleInstance

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    context("FX observable properties reflect core player state") {

        test("initial properties are at default values") {
            val corePlayer = corePlayerMock(AudioItemPlayer.Status.UNKNOWN, Duration.ZERO)
            withPlayer(corePlayer) { player ->
                player.statusProperty.get() shouldBe AudioItemPlayer.Status.UNKNOWN
                player.currentTimeProperty.get() shouldBe FxDuration.ZERO
                player.volumeProperty.get() shouldBe 1.0
            }
        }

        withData(
            nameFn = { it.name },
            TransportCase(
                "play",
                AudioItemPlayer.Status.PLAYING,
                expectedMillis = 100.0,
                currentTime = Duration.ofMillis(100),
                total = Duration.ofSeconds(10),
                act = { player, core ->
                    val audioItem =
                        mockk<ReactiveAudioItem<*>>(relaxed = true) {
                            every { path } returns Files.createTempFile("test", ".wav")
                            every { fileName } returns "test.wav"
                            every { extension } returns "wav"
                            every { encoding } returns null
                            every { encoder } returns null
                        }
                    player.play(audioItem)
                    verify { core.play(audioItem) }
                    audioItem.path.toFile().delete()
                }
            ),
            TransportCase(
                "pause",
                AudioItemPlayer.Status.PAUSED,
                expectedMillis = 500.0,
                currentTime = Duration.ofMillis(500),
                act = { player, core ->
                    player.pause()
                    verify { core.pause() }
                }
            ),
            TransportCase(
                "stop",
                AudioItemPlayer.Status.STOPPED,
                expectedMillis = 0.0,
                currentTime = Duration.ZERO,
                act = { player, core ->
                    player.stop()
                    verify { core.stop() }
                }
            ),
            TransportCase(
                "resume",
                AudioItemPlayer.Status.PLAYING,
                expectedMillis = 750.0,
                currentTime = Duration.ofMillis(750),
                act = { player, core ->
                    player.resume()
                    verify { core.resume() }
                }
            )
        ) { case ->
            val corePlayer = corePlayerMock(case.expectedStatus, case.currentTime, case.total)
            withPlayer(corePlayer) { player ->
                case.act(player, corePlayer)

                eventually(1.seconds) {
                    player.statusProperty.get() shouldBe case.expectedStatus
                    player.currentTimeProperty.get().toMillis() shouldBe case.expectedMillis
                }
            }
        }

        test("setVolume updates FX property and delegates to core player") {
            val corePlayer = mockk<CoreAudioItemPlayer>(relaxed = true)
            withPlayer(corePlayer) { player ->
                player.setVolume(0.75)

                eventually(1.seconds) {
                    player.volumeProperty.get() shouldBe 0.75
                }

                verify { corePlayer.setVolume(0.75) }
            }
        }

        test("seek delegates to core player") {
            val corePlayer = mockk<CoreAudioItemPlayer>(relaxed = true)
            val seekPosition = Duration.ofSeconds(30)
            withPlayer(corePlayer) { player ->
                player.seek(seekPosition)

                verify { corePlayer.seek(seekPosition) }
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

            withPlayer(FXAudioItemPlayer()) { player ->
                shouldThrow<UnsupportedAudioPlaybackException> {
                    player.play(item)
                }
            }
        }
    }

    context("detectCodec delegates to AudioItemPlayer") {
        withData(
            nameFn = { it.name },
            // container-only single-codec formats
            CodecCase("detects MP3 for mp3 files", "mp3", "MPEG-1 Layer 3", null, AudioFileCodec.MP3),
            CodecCase("detects PCM for wav files", "wav", "PCM", null, AudioFileCodec.PCM),
            CodecCase("detects Vorbis for ogg files", "ogg", "Vorbis", null, AudioFileCodec.VORBIS),
            // AAC / ALAC / FLAC / Opus by encoding
            CodecCase("detects AAC for M4A files", "m4a", "AAC", null, AudioFileCodec.AAC),
            CodecCase("detects ALAC by Apple encoding prefix", "m4a", "Apple ALAC", null, AudioFileCodec.ALAC, expectedPlayable = false),
            CodecCase("codec patterns match case-insensitively", "m4a", "apple alac", null, AudioFileCodec.ALAC),
            CodecCase("detects AAC via MPEG-4 encoding prefix", "m4a", "MPEG-4 AAC", null, AudioFileCodec.AAC, expectedPlayable = true),
            // Regression: encoder = "iTunes ..." must not be treated as proof of ALAC; a regular AAC
            // M4A from iTunes carries an "iTunes ..." encoder too.
            CodecCase("iTunes-encoded AAC files remain playable AAC", "m4a", "AAC", "iTunes 12.10", AudioFileCodec.AAC, expectedPlayable = true),
            CodecCase("detects Opus encoding regardless of container", "ogg", "Opus", null, AudioFileCodec.OPUS, expectedPlayable = false),
            CodecCase("detects FLAC-in-M4A as FLAC and marks it unplayable", "m4a", "FLAC", null, AudioFileCodec.FLAC, expectedPlayable = false),
            CodecCase("native FLAC files remain playable", "flac", "FLAC", null, AudioFileCodec.FLAC, expectedPlayable = true)
        ) { (_, ext, encodingValue, encoderValue, expectedCodec, expectedPlayable) ->
            val item =
                mockk<ReactiveAudioItem<*>>(relaxed = true) {
                    every { extension } returns ext
                    every { encoding } returns encodingValue
                    every { encoder } returns encoderValue
                }

            // detectCodec is a pure function of tag state; asserting twice pins its idempotence.
            AudioItemPlayer.detectCodec(item) shouldBe expectedCodec
            AudioItemPlayer.detectCodec(item) shouldBe expectedCodec
            expectedPlayable?.let { AudioItemPlayer.isPlayable(item) shouldBe it }
        }
    }
})

private data class CodecCase(
    val name: String,
    val extension: String,
    val encoding: String,
    val encoder: String?,
    val expectedCodec: AudioFileCodec,
    val expectedPlayable: Boolean? = null
)

private class TransportCase(
    val name: String,
    val expectedStatus: AudioItemPlayer.Status,
    val expectedMillis: Double,
    val currentTime: Duration,
    val total: Duration = Duration.ZERO,
    val act: (FXAudioItemPlayer, CoreAudioItemPlayer) -> Unit
)

/** Builds a relaxed [CoreAudioItemPlayer] mock stubbing the three properties the FX player mirrors. */
private fun corePlayerMock(
    status: AudioItemPlayer.Status,
    currentTime: Duration,
    total: Duration = Duration.ZERO
): CoreAudioItemPlayer =
    mockk(relaxed = true) {
        every { status() } returns status
        every { getCurrentTime() } returns currentTime
        every { totalDuration } returns total
    }

/** Runs [block] against a freshly-wrapped [FXAudioItemPlayer], disposing it afterward (mirrors `.use`). */
private suspend fun withPlayer(core: CoreAudioItemPlayer, block: suspend (FXAudioItemPlayer) -> Unit) =
    withPlayer(FXAudioItemPlayer(core), block)

private suspend fun withPlayer(player: FXAudioItemPlayer, block: suspend (FXAudioItemPlayer) -> Unit) {
    try {
        block(player)
    } finally {
        player.dispose()
    }
}