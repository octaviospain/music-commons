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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import java.io.File

/**
 * Unit tests for [Mp3PcmStreamSeeker] covering the Xing TOC path, CBR frame-scan fallback,
 * edge cases (non-mp3 extension, non-positive offset), and graceful handling of malformed input.
 */
@DisplayName("Mp3PcmStreamSeeker")
internal class Mp3PcmStreamSeekerTest : FunSpec({

    data class GuardCase(val fixture: String, val offset: Long)

    context("Mp3PcmStreamSeeker returns null for guarded inputs") {
        withData(
            nameFn = { "${it.fixture.substringAfterLast('/')} at offset ${it.offset}" },
            GuardCase("/testfiles/testeable.flac", 100_000L),
            GuardCase("/testfiles/testeable.mp3", 0L),
            GuardCase("/testfiles/testeable.mp3", -1L)
        ) { (fixture, offset) ->
            val file = ArbitraryAudioFile.getResourceAsFile(fixture)
            Mp3PcmStreamSeeker.open(file, offset).shouldBeNull()
        }
    }

    test("Mp3PcmStreamSeeker Xing TOC seek returns a positive startByteOffset that increases with the requested offset") {
        // testeable.mp3 has a Xing header at byte 613740 — verifies the TOC path
        val mp3File = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable.mp3")

        val smallOffset = 50_000L
        val largeOffset = 500_000L

        val smallResult = Mp3PcmStreamSeeker.open(mp3File, smallOffset)
        val largeResult = Mp3PcmStreamSeeker.open(mp3File, largeOffset)

        smallResult.shouldNotBeNull()
        largeResult.shouldNotBeNull()

        smallResult.startByteOffset shouldBeGreaterThan 0L
        largeResult.startByteOffset shouldBeGreaterThan smallResult.startByteOffset

        smallResult.stream.use { }
        largeResult.stream.use { }
    }

    test("Mp3PcmStreamSeeker Xing TOC seek yields a fully specified PCM format at every probed offset") {
        // Regression: a TOC offset that lands mid-frame previously let the AAC provider win the
        // sync-word collision and produced a 0-channel / 0-frame-size format, which crashes
        // SourceDataLine.open. The resync + prioritized MPEG decoder must yield a usable format.
        val mp3File = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable.mp3")

        listOf(50_000L, 250_000L, 500_000L, 750_000L, 1_000_000L).forEach { offset ->
            withClue("offset=$offset") {
                val result = Mp3PcmStreamSeeker.open(mp3File, offset).shouldNotBeNull()
                result.stream.format.frameSize shouldBeGreaterThan 0
                result.stream.format.channels shouldBeGreaterThan 0
                result.stream.use { }
            }
        }
    }

    test("Mp3PcmStreamSeeker frame-scan fallback returns a non-null result for the CBR fixture") {
        // testeable_cbr.mp3 has no Xing/Info header — exercises the frame-scan path
        val cbrFile = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable_cbr.mp3")

        val result = Mp3PcmStreamSeeker.open(cbrFile, 100_000L)

        result.shouldNotBeNull()
        result.stream.use { }
    }

    test("Mp3PcmStreamSeeker returns null without hanging for a truncated or garbage file") {
        // Create a temp file with random bytes (no valid MPEG frames)
        val garbageFile =
            File.createTempFile("garbage-mp3-test", ".mp3").apply {
                deleteOnExit()
                writeBytes(ByteArray(4096) { it.toByte() })
            }

        val result = Mp3PcmStreamSeeker.open(garbageFile, 100_000L)

        // Must return null (not hang) — MAX_SCAN_FRAMES caps the scan
        result.shouldBeNull()
    }

    test("Mp3PcmStreamSeeker CBR frame-scan startByteOffset is less than requested offset") {
        // The frame scan returns the frame that would contain the requested position;
        // the pcm offset at that frame start is <= requestedByteOffset
        val cbrFile = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable_cbr.mp3")
        val requestedOffset = 200_000L

        val result = Mp3PcmStreamSeeker.open(cbrFile, requestedOffset)

        result.shouldNotBeNull()
        // startByteOffset is the frame-start PCM offset; it must be non-negative and strictly
        // below the requested offset (the scan stops at the frame that would contain the target).
        result.startByteOffset shouldBeGreaterThan -1L
        requestedOffset shouldBeGreaterThan result.startByteOffset
        result.stream.use { }
    }

    test("Mp3PcmStreamSeeker registered in CoreAudioItemPlayer seeker chain after FlacPcmStreamSeeker") {
        val seekersField =
            CoreAudioItemPlayer::class.java.getDeclaredField("seekers").apply {
                isAccessible = true
            }
        val player = CoreAudioItemPlayer()
        try {
            @Suppress("UNCHECKED_CAST")
            val seekers = seekersField.get(player) as List<PcmStreamSeeker>
            val flacIndex = seekers.indexOf(FlacPcmStreamSeeker)
            val mp3Index = seekers.indexOf(Mp3PcmStreamSeeker)

            flacIndex shouldBeGreaterThan -1
            mp3Index shouldBeGreaterThan flacIndex
        } finally {
            player.dispose()
        }
    }
})