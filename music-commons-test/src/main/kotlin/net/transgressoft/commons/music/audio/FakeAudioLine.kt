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

package net.transgressoft.commons.music.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Control
import javax.sound.sampled.DataLine
import javax.sound.sampled.Line
import javax.sound.sampled.LineListener
import javax.sound.sampled.SourceDataLine

/**
 * [SourceDataLine] test double that replaces real hardware output with a virtual clock.
 *
 * Position advances deterministically from bytes written ÷ frame size × frame rate of the
 * open [AudioFormat]; [write] is non-blocking and always accepts all bytes. Use via the
 * [CoreAudioItemPlayer][net.transgressoft.commons.media.player.CoreAudioItemPlayer]
 * `lineFactory` constructor parameter to run playback state-machine tests without
 * real audio hardware or thread sleeps.
 *
 * Virtual-clock formula (D-01):
 * `getMicrosecondPosition() = (totalBytesWritten × 1_000_000.0 / frameSize / frameRate).toLong()`
 *
 * The clock halts while the line is stopped and resumes on [start] (D-03). [flush] resets
 * the accumulated byte count to zero. [drain] is a no-op. [available] and [getBufferSize]
 * return [Int.MAX_VALUE] so the pump thread never simulates backpressure (D-04).
 *
 * @see SourceDataLine
 */
class FakeAudioLine : SourceDataLine {

    private var format: AudioFormat? = null
    private var open = false
    private var running = false
    private var totalBytesWritten = 0L

    override fun open(format: AudioFormat, bufferSize: Int) {
        this.format = format
        this.open = true
    }

    override fun open(format: AudioFormat) = open(format, 4096)

    override fun open() = open(AudioFormat(44100f, 16, 2, true, false))

    override fun close() {
        open = false
        running = false
    }

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun write(b: ByteArray, off: Int, len: Int): Int {
        require(len >= 0) { "len must be non-negative, got $len" }
        if (!running) return 0
        totalBytesWritten += len
        return len
    }

    override fun drain() {
        // No-op: virtual line has no buffered bytes to wait for (D-03)
    }

    override fun flush() {
        totalBytesWritten = 0L
    }

    override fun getMicrosecondPosition(): Long {
        val fmt = format ?: return 0L
        val fs = fmt.frameSize.takeIf { it > 0 } ?: return 0L
        val rate = fmt.frameRate
        if (rate <= 0f) return 0L
        return (totalBytesWritten * 1_000_000.0 / fs / rate).toLong()
    }

    override fun getLongFramePosition(): Long {
        val fmt = format ?: return 0L
        val fs = fmt.frameSize.takeIf { it > 0 } ?: return 0L
        return totalBytesWritten / fs
    }

    override fun getFramePosition(): Int = getLongFramePosition().toInt()

    override fun isOpen(): Boolean = open

    override fun isRunning(): Boolean = running

    override fun isActive(): Boolean = running

    override fun available(): Int = Int.MAX_VALUE

    override fun getBufferSize(): Int = Int.MAX_VALUE

    override fun getFormat(): AudioFormat = format ?: AudioFormat(44100f, 16, 2, true, false)

    override fun getLevel(): Float = AudioSystem.NOT_SPECIFIED.toFloat()

    override fun getLineInfo(): Line.Info = DataLine.Info(SourceDataLine::class.java, format)

    override fun getControls(): Array<Control> = emptyArray()

    override fun getControl(type: Control.Type): Control = throw IllegalArgumentException("No controls supported")

    override fun isControlSupported(type: Control.Type): Boolean = false

    override fun addLineListener(listener: LineListener) {}

    override fun removeLineListener(listener: LineListener) {}
}