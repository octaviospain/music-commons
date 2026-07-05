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
import io.kotest.assertions.withClue
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Unit tests for [OggPcmStreamSeeker] covering bisection seek within a real Ogg Vorbis file,
 * guard clauses (non-ogg extension, non-positive offset), and graceful handling of malformed input.
 */
@DisplayName("OggPcmStreamSeeker")
internal class OggPcmStreamSeekerTest : FunSpec({

    data class GuardCase(val fixture: String, val offset: Long)

    context("OggPcmStreamSeeker returns null for guarded inputs") {
        withData(
            nameFn = { "${it.fixture.substringAfterLast('/')} at offset ${it.offset}" },
            GuardCase("/testfiles/testeable.flac", 100_000L),
            GuardCase("/testfiles/testeable_vorbis.ogg", 0L),
            GuardCase("/testfiles/testeable_vorbis.ogg", -1L)
        ) { (fixture, offset) ->
            val file = ArbitraryAudioFile.getResourceAsFile(fixture)
            OggPcmStreamSeeker.open(file, offset).shouldBeNull()
        }
    }

    test("OggPcmStreamSeeker bisection startByteOffset is at or before the requested offset") {
        val oggFile = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable_vorbis.ogg")
        // Request a mid-file PCM offset (~50% through)
        // testeable_vorbis.ogg: 44100Hz, 2 channels, 16-bit = 4 bytes/frame
        // File has ~27s of audio → ~27 * 44100 * 4 ≈ 4,762,800 PCM bytes
        val requestedOffset = 2_000_000L

        val result = OggPcmStreamSeeker.open(oggFile, requestedOffset)

        result.shouldNotBeNull()
        result.startByteOffset shouldBeLessThanOrEqualTo requestedOffset
        result.stream.use { }
    }

    test("OggPcmStreamSeeker bisection startByteOffset is within one Vorbis page of the requested offset") {
        val oggFile = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable_vorbis.ogg")
        val requestedOffset = 2_000_000L

        val result = OggPcmStreamSeeker.open(oggFile, requestedOffset)

        result.shouldNotBeNull()
        // One Vorbis page contains up to ~45000 samples × 4 bytes/frame ≈ 200000 bytes of PCM.
        // The seeker must land at or before the requested offset, within one page distance.
        (requestedOffset - result.startByteOffset) shouldBeLessThanOrEqualTo 200_000L
        result.stream.use { }
    }

    test("OggPcmStreamSeeker lands sample-accurately at the requested offset for in-range targets") {
        // Regression: the bisection used to stop at the 64 KB window without refining, landing a
        // whole window (seconds, growing with target position) before the requested point. The
        // seeker now drains to the exact frame-aligned target, so the gap is at most one frame.
        val oggFile = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable_vorbis.ogg")
        val frameSize = 4L // 44100Hz, 2 channels, 16-bit

        listOf(400_000L, 1_000_000L, 2_000_000L).forEach { requestedOffset ->
            withClue("offset=$requestedOffset") {
                val result = OggPcmStreamSeeker.open(oggFile, requestedOffset).shouldNotBeNull()
                val alignedRequest = requestedOffset - requestedOffset % frameSize
                // Exact frame-aligned landing, never overshooting the request.
                result.startByteOffset shouldBe alignedRequest
                result.stream.use { }
            }
        }
    }

    test("OggPcmStreamSeeker returns a larger startByteOffset for a larger requested offset") {
        val oggFile = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable_vorbis.ogg")

        val smallOffset = 500_000L
        val largeOffset = 3_000_000L

        val smallResult = OggPcmStreamSeeker.open(oggFile, smallOffset)
        val largeResult = OggPcmStreamSeeker.open(oggFile, largeOffset)

        smallResult.shouldNotBeNull()
        largeResult.shouldNotBeNull()
        largeResult.startByteOffset shouldBeGreaterThan smallResult.startByteOffset

        smallResult.stream.use { }
        largeResult.stream.use { }
    }

    test("OggPcmStreamSeeker returns null without hanging for a truncated or garbage file") {
        val garbageFile =
            File.createTempFile("garbage-ogg-test", ".ogg").apply {
                deleteOnExit()
                writeBytes(ByteArray(4096) { it.toByte() })
            }

        val result = OggPcmStreamSeeker.open(garbageFile, 100_000L)

        // Must return null (not hang) — MAX_BISECT_ITERATIONS caps the bisection
        result.shouldBeNull()
    }
})