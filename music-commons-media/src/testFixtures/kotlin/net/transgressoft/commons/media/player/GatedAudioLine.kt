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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.sampled.SourceDataLine

/**
 * Holds decoded PCM at the first output write until a test releases playback or closes the line.
 *
 * Unlike [FakeAudioLine]'s instant drain, this boundary keeps PLAYING observable regardless of
 * thread scheduling while still exercising the real decoder. Closing releases the gate so failed
 * assertions cannot leave a pump blocked indefinitely.
 */
class GatedAudioLine(private val line: FakeAudioLine = FakeAudioLine()) : SourceDataLine by line {

    private val writeStarted = CountDownLatch(1)
    private val writesReleased = CountDownLatch(1)

    override fun write(b: ByteArray, off: Int, len: Int): Int {
        writeStarted.countDown()
        writesReleased.await()
        return line.write(b, off, len)
    }

    fun awaitWrite(timeout: Long, unit: TimeUnit): Boolean = writeStarted.await(timeout, unit)

    fun releaseWrites() {
        writesReleased.countDown()
    }

    override fun close() {
        line.close()
        releaseWrites()
    }
}