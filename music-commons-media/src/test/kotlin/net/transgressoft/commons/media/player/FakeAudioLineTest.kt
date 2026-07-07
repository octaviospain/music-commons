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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import javax.sound.sampled.AudioFormat

/**
 * Isolated unit tests for [FakeAudioLine] that prove the virtual-clock formula and
 * the start/stop/flush gating semantics without real audio hardware.
 */
class FakeAudioLineTest : StringSpec({

    val format44100Hz16Bit2Ch = AudioFormat(44100f, 16, 2, true, false)

    "FakeAudioLine getMicrosecondPosition returns 0 before open" {
        val line = FakeAudioLine()
        line.getMicrosecondPosition() shouldBe 0L
    }

    "FakeAudioLine getMicrosecondPosition advances by the virtual-clock formula after open and start" {
        val line = FakeAudioLine()
        line.open(format44100Hz16Bit2Ch)
        line.start()
        val buffer = ByteArray(4400)
        line.write(buffer, 0, 4400)
        // (4400 * 1_000_000) / frameSize(4) / frameRate(44100) = 4400 * 1_000_000 / 4 / 44100 = 24943
        val expectedMicros = 4400L * 1_000_000L / 4L / 44100L
        line.getMicrosecondPosition() shouldBe expectedMicros
    }

    "FakeAudioLine getLongFramePosition advances correctly after write" {
        val line = FakeAudioLine()
        line.open(format44100Hz16Bit2Ch)
        line.start()
        val buffer = ByteArray(4400)
        line.write(buffer, 0, 4400)
        // 4400 bytes / frameSize(4) = 1100 frames
        line.getLongFramePosition() shouldBe 1100L
    }

    "FakeAudioLine flush resets getMicrosecondPosition to 0" {
        val line = FakeAudioLine()
        line.open(format44100Hz16Bit2Ch)
        line.start()
        val buffer = ByteArray(4400)
        line.write(buffer, 0, 4400)
        line.flush()
        line.getMicrosecondPosition() shouldBe 0L
    }

    "FakeAudioLine stop halts the clock — write after stop does not advance position" {
        val line = FakeAudioLine()
        line.open(format44100Hz16Bit2Ch)
        line.start()
        val buffer = ByteArray(4400)
        line.write(buffer, 0, 4400)
        val positionAfterStart = line.getMicrosecondPosition()
        line.stop()
        val writeResult = line.write(buffer, 0, 4400)
        writeResult shouldBe 0
        line.getMicrosecondPosition() shouldBe positionAfterStart
    }

    "FakeAudioLine start resumes the clock after stop" {
        val line = FakeAudioLine()
        line.open(format44100Hz16Bit2Ch)
        line.start()
        val buffer = ByteArray(4400)
        line.write(buffer, 0, 4400)
        line.stop()
        // write while stopped — should not advance
        line.write(buffer, 0, 4400)
        val positionAfterStop = line.getMicrosecondPosition()
        line.start()
        // write while running again — should advance
        line.write(buffer, 0, 4400)
        val positionAfterResume = line.getMicrosecondPosition()
        positionAfterResume shouldBe positionAfterStop + 4400L * 1_000_000L / 4L / 44100L
    }

    "FakeAudioLine write returns len when running and 0 when stopped" {
        val line = FakeAudioLine()
        line.open(format44100Hz16Bit2Ch)
        val buffer = ByteArray(100)
        // Before start: not running — write returns 0
        line.write(buffer, 0, 100) shouldBe 0
        line.start()
        // Running: returns len
        line.write(buffer, 0, 100) shouldBe 100
        line.stop()
        // Stopped: returns 0
        line.write(buffer, 0, 100) shouldBe 0
    }

    "FakeAudioLine available and getBufferSize return Int.MAX_VALUE" {
        val line = FakeAudioLine()
        line.available() shouldBe Int.MAX_VALUE
        line.getBufferSize() shouldBe Int.MAX_VALUE
    }

    "FakeAudioLine open and close track isOpen and isRunning lifecycle" {
        val line = FakeAudioLine()
        line.isOpen shouldBe false
        line.isRunning shouldBe false
        line.open(format44100Hz16Bit2Ch)
        line.isOpen shouldBe true
        line.start()
        line.isRunning shouldBe true
        line.close()
        line.isOpen shouldBe false
        line.isRunning shouldBe false
    }

    "FakeAudioLine drain returns immediately without blocking" {
        val line = FakeAudioLine()
        line.open(format44100Hz16Bit2Ch)
        line.start()
        val buffer = ByteArray(4400)
        line.write(buffer, 0, 4400)
        val positionBeforeDrain = line.getMicrosecondPosition()
        line.drain()
        // drain() is a no-op: position must not change
        line.getMicrosecondPosition() shouldBe positionBeforeDrain
    }
})