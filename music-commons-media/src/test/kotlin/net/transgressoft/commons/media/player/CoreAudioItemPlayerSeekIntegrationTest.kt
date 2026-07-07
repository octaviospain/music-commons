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

import net.transgressoft.commons.music.audio.ArbitraryAudioFile
import net.transgressoft.commons.music.audio.FakeAudioLine
import net.transgressoft.commons.music.audio.audioItem
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.io.InputStream
import java.time.Duration
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration.Companion.seconds

private val PCM_FORMAT_SEEK = AudioFormat(44_100f, 16, 2, true, false)

/**
 * A PCM stream whose reads always return 0 (no data yet, not EOF), causing the pump to loop
 * at [PlaybackPump]'s [ZERO_READ_BACKOFF_MILLIS][PlaybackPump.ZERO_READ_BACKOFF_MILLIS]-ms
 * intervals and re-check [CoreAudioItemPlayer.seekTargetMillis] on every iteration.
 *
 * [FakeAudioLine] drains real fixtures near-instantly, which races the pump to READY before a
 * seek can be consumed. A zero-read source keeps the player in PLAYING indefinitely without
 * blocking the pump thread, so the test can issue a seek and the pump picks it up on the next
 * loop iteration rather than after the fixture drains to EOF.
 */
private fun zeroPcmStream(): AudioInputStream {
    val zero =
        object : InputStream() {
            override fun read(): Int = 0

            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
    return AudioInputStream(zero, PCM_FORMAT_SEEK, AudioSystem.NOT_SPECIFIED.toLong())
}

/**
 * End-to-end seek integration tests for [CoreAudioItemPlayer] that exercise the frame-accurate
 * [Mp3PcmStreamSeeker] and [OggPcmStreamSeeker] code paths through the real decode pipeline.
 *
 * Each test drives a seek-while-playing flow. The initial PCM source is a zero-read stream that
 * keeps the pump looping in PLAYING without blocking it — the pump checks
 * [CoreAudioItemPlayer.seekTargetMillis] on every zero-read iteration. When a seek is issued,
 * the pump consumes [CoreAudioItemPlayer.seekTargetMillis] and calls [reopenAtSeekTarget], which
 * calls [openStreamSession] against the real fixture file via the registered seeker chain
 * ([Mp3PcmStreamSeeker] or [OggPcmStreamSeeker]). The seeker sets
 * [CoreAudioItemPlayer.framePosition] to the actual landed PCM byte offset, and [openLine]
 * copies that into [CoreAudioItemPlayer.lineStartFrameOffset]. After the pump writes the first
 * buffer from the seeked stream it clears [CoreAudioItemPlayer.seekPreviewMillis].
 *
 * The assertion checks [CoreAudioItemPlayer.lineStartFrameOffset] once
 * [CoreAudioItemPlayer.seekPreviewMillis] has cleared, converting it to milliseconds with the
 * same formula used by [CoreAudioItemPlayer.getCurrentTime]. This value reflects what the seeker
 * actually produced — not the preview shortcut that [seek] sets directly — so the assertion
 * can only pass when the real per-format seeker positioned the stream correctly.
 *
 * Seek-precision tolerance is ±500 ms for both formats, which comfortably covers one MP3 frame
 * (~26 ms at 128 kbps) and the OGG granulepos drain margin.
 */
@DisplayName("CoreAudioItemPlayer seek integration")
internal class CoreAudioItemPlayerSeekIntegrationTest : FunSpec({

    test("CoreAudioItemPlayer seeks CBR MP3 to correct frame boundary via Mp3PcmStreamSeeker") {
        // testeable_cbr.mp3 has no Xing/Info header — exercises the Mp3PcmStreamSeeker
        // frame-scan fallback (CBR path, not TOC path).
        val cbrFile = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable_cbr.mp3")
        val audioItem = Arb.audioItem { path = cbrFile.toPath() }.next()

        // zeroPcmStream holds the pump in PLAYING without blocking it, so seek() is consumed
        // naturally by the pump's seek-target check at the top of its main loop.
        // decodeToPcmStream is NOT used for the initial open; the seeker chain uses the real
        // fixture file directly when processing the seek, so Mp3PcmStreamSeeker is exercised.
        val player = testPlayer(pcmStreamFactory = { zeroPcmStream() }) { FakeAudioLine() }
        player.setVolume(0.0)

        try {
            player.play(audioItem)

            // Wait for the pump to enter the streaming loop (pcmFormat resolved from zeroPcmStream).
            eventually(3.seconds) {
                player.pcmFormat.get() shouldNotBe null
            }

            val targetMs = 5_000L
            val toleranceMs = 500L

            // Issue seek while PLAYING so the pump consumes seekTargetMillis in its main loop.
            // Pausing before seeking would leave seekPreviewMillis permanently set (pump never
            // runs reopenAtSeekTarget while PAUSED), making getCurrentTime() return the preview
            // value rather than the seeker-computed lineStartFrameOffset.
            player.seek(Duration.ofMillis(targetMs))

            // Wait for the pump to process the seek. lineStartFrameOffset > 0 confirms the seeker
            // set framePosition from seekResult.startByteOffset (not from a zero-offset initial open).
            // seekPreviewMillis < 0 confirms the pump wrote at least one buffer from the seeked stream.
            eventually(5.seconds) {
                player.lineStartFrameOffset shouldBeGreaterThan 0L
                player.seekPreviewMillis shouldBe -1L
            }

            // lineStartFrameOffset is the seeker's actual output, copied into the player by openLine().
            // Converting it to ms with the same formula as getCurrentTime() proves the seeker
            // repositioned the stream — not that seekPreviewMillis was echoed back.
            val fs = player.frameSize.toLong()
            val rate = player.frameRate
            val landedMs = (player.lineStartFrameOffset * 1000.0 / fs / rate).toLong()
            landedMs shouldBeGreaterThan (targetMs - toleranceMs)
            landedMs shouldBeLessThan (targetMs + toleranceMs)
        } finally {
            player.dispose()
        }
    }

    test("CoreAudioItemPlayer seeks Vorbis OGG to correct PCM offset via OggPcmStreamSeeker") {
        // testeable_vorbis.ogg (~24.7 s) exercises the OggPcmStreamSeeker granulepos bisection
        // and forward-drain path to a sample-accurate PCM offset.
        val oggFile = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable_vorbis.ogg")
        val audioItem = Arb.audioItem { path = oggFile.toPath() }.next()

        val player = testPlayer(pcmStreamFactory = { zeroPcmStream() }) { FakeAudioLine() }
        player.setVolume(0.0)

        try {
            player.play(audioItem)

            eventually(3.seconds) {
                player.pcmFormat.get() shouldNotBe null
            }

            // Pick a mid-track target well within the fixture duration.
            val targetMs = 10_000L
            val toleranceMs = 500L

            player.seek(Duration.ofMillis(targetMs))

            eventually(15.seconds) {
                player.lineStartFrameOffset shouldBeGreaterThan 0L
                player.seekPreviewMillis shouldBe -1L
            }

            val fs = player.frameSize.toLong()
            val rate = player.frameRate
            val landedMs = (player.lineStartFrameOffset * 1000.0 / fs / rate).toLong()
            landedMs shouldBeGreaterThan (targetMs - toleranceMs)
            landedMs shouldBeLessThan (targetMs + toleranceMs)
        } finally {
            player.dispose()
        }
    }
})