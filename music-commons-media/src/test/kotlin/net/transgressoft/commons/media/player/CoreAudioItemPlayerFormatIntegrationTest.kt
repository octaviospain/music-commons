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
import net.transgressoft.commons.music.audio.FakeAudioLine
import net.transgressoft.commons.music.audio.audioItem
import net.transgressoft.commons.music.player.AudioItemPlayer.Status
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.DisplayName
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.seconds

/**
 * Per-format end-to-end integration tests for [CoreAudioItemPlayer] using [FakeAudioLine] to
 * exercise the real SPI decode pipeline without audio hardware.
 *
 * Each test row drives the real [decodeToPcmStream] factory so actual codec-specific decoding
 * happens; only the hardware output sink is replaced by [FakeAudioLine]. The test matrix covers
 * the 6 formats for which [net.transgressoft.commons.music.player.AudioItemPlayer.isPlayable]
 * returns `true`: MP3 VBR, MP3 CBR, FLAC, AAC/M4A, WAV, and Vorbis/OGG.
 *
 * ALAC (`testeable_alac.m4a`) and Opus (`testeable_opus.ogg`) are excluded from the playback
 * matrix because [net.transgressoft.commons.music.player.AudioItemPlayer.isPlayable] returns
 * `false` for them — calling `play()` on an ALAC or Opus item throws
 * [net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException]. Their SPI-decode
 * coverage lives in [SpiProviderVerificationTest] at the `AudioSystem.getAudioInputStream()` level.
 */
@DisplayName("CoreAudioItemPlayer per-format integration")
internal class CoreAudioItemPlayerFormatIntegrationTest : FunSpec({

    /**
     * Describes a single playable format row in the test matrix.
     *
     * @param label human-readable format name used in test output
     * @param resourcePath classpath-relative path under `/testfiles/`
     * @param expectedMs ground-truth duration in milliseconds (from ffprobe)
     * @param toleranceMs acceptable deviation from [expectedMs] in milliseconds
     */
    data class FormatCase(
        val label: String,
        val resourcePath: String,
        val expectedMs: Long,
        val toleranceMs: Long
    )

    context("CoreAudioItemPlayer plays each bundled format end-to-end with FakeAudioLine") {
        withData(
            nameFn = { it.label },
            // WAV — PCM byte-aligned, near-exact duration probe
            FormatCase("WAV", "testeable.wav", expectedMs = 8_787L, toleranceMs = 10L),
            // FLAC — one FLAC frame ≤ 4096 samples (~93 ms at 44.1 kHz); decoder reports close to exact
            FormatCase("FLAC", "testeable.flac", expectedMs = 27_609L, toleranceMs = 50L),
            // MP3 VBR — Xing header duration probe reliable; one MPEG frame ~26 ms
            FormatCase("MP3 VBR", "testeable.mp3", expectedMs = 16_867L, toleranceMs = 100L),
            // MP3 CBR — DurationProber resolves asynchronously (no Xing header); wider tolerance
            FormatCase("MP3 CBR", "testeable_cbr.mp3", expectedMs = 16_901L, toleranceMs = 500L),
            // Vorbis-OGG — granulepos accurate; OGG container duration reads well
            FormatCase("Vorbis-OGG", "testeable_vorbis.ogg", expectedMs = 24_705L, toleranceMs = 100L),
            // AAC/M4A — DurationProber probes asynchronously for M4A; wider tolerance
            FormatCase("AAC/M4A", "testeable_aac.m4a", expectedMs = 41_559L, toleranceMs = 500L)
        ) { (_, resourcePath, expectedMs, toleranceMs) ->
            val player = testPlayer(::decodeToPcmStream) { FakeAudioLine() }
            val file = ArbitraryAudioFile.getResourceAsFile("/testfiles/$resourcePath")
            val audioItem = Arb.audioItem { path = file.toPath() }.next()
            player.setVolume(0.0)

            try {
                player.play(audioItem)
                player.status() shouldBe Status.PLAYING

                // CBR/AAC resolve totalDuration asynchronously via a background probe thread;
                // wait for the value to settle before asserting the tolerance window.
                eventually(5.seconds) {
                    player.totalDuration.toMillis() shouldBeGreaterThan 0L
                }
                val durationMs = player.totalDuration.toMillis()
                durationMs shouldBeGreaterThan (expectedMs - toleranceMs)
                durationMs shouldBeLessThan (expectedMs + toleranceMs)

                // FakeAudioLine drains near-instantly; wait for the pump to reach READY.
                eventually(10.seconds) {
                    player.status() shouldBe Status.READY
                }
            } finally {
                player.dispose()
            }
        }
    }
})

/**
 * PCM-to-line smoke test for [CoreAudioItemPlayer] that opens a real [javax.sound.sampled.SourceDataLine].
 *
 * Excluded from default CI via `-PexcludeAudioHardware=true`; run on a machine with
 * audio output to verify the full JavaSound SPI pipeline end-to-end.
 */
@Tags("audio-hardware")
internal class CoreAudioItemPlayerFormatSmokeTest : FunSpec({

    test("PCM-to-line smoke: MP3 CBR plays through real SourceDataLine") {
        val player = CoreAudioItemPlayer()
        val file = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable_cbr.mp3")
        val audioItem = Arb.audioItem { path = file.toPath() }.next()
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