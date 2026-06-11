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

import mu.KotlinLogging
import javax.sound.sampled.AudioFormat

/**
 * Applies a linear volume gain to a raw PCM byte buffer in-place, supporting 8-bit unsigned,
 * 16-bit, 24-bit, and 32-bit signed PCM in both big-endian and little-endian byte order.
 *
 * Samples outside the representable range for their bit depth are clamped. Gain values of
 * exactly 0.0 and 1.0 are handled as fast paths (fill with zeros and no-op respectively).
 */
internal object PcmVolume {

    private val logger = KotlinLogging.logger {}

    /**
     * Applies [gain] to the first [chunk] bytes of [buffer] according to [format].
     * The modification is performed in-place. Has no effect when [gain] is 1.0.
     *
     * @param buffer the raw PCM byte buffer
     * @param chunk number of bytes to process (must not exceed [buffer] size)
     * @param format the PCM audio format describing encoding, sample size, and byte order
     * @param gain linear amplitude multiplier in the range [0.0, 1.0]
     */
    fun apply(buffer: ByteArray, chunk: Int, format: AudioFormat, gain: Float) {
        // Fail fast on a misuse here rather than with an opaque AIOOBE deep in the scaling loop.
        require(chunk in 0..buffer.size) { "chunk ($chunk) must be in 0..${buffer.size}" }
        if (gain == 1.0f) return
        if (gain == 0.0f) {
            buffer.fill(0, 0, chunk)
            return
        }
        val sampleSizeInBits = format.sampleSizeInBits
        if (sampleSizeInBits <= 0 || sampleSizeInBits % 8 != 0) {
            logger.debug {
                "Volume control skipped: sample size ${format.sampleSizeInBits}-bit not supported (requires byte-aligned PCM)"
            }
            return
        }

        when (val bytesPerSample = sampleSizeInBits / 8) {
            1 if format.encoding == AudioFormat.Encoding.PCM_UNSIGNED -> {
                for (i in 0 until chunk) {
                    val signed = (buffer[i].toInt() and 0xFF) - 128
                    val scaled = (signed * gain).toInt().coerceIn(-128, 127)
                    buffer[i] = (scaled + 128).toByte()
                }
            }
            in 1..4 -> scaleSignedSamples(buffer, chunk, bytesPerSample, gain, format.isBigEndian)
            else -> {
                logger.debug {
                    "Volume control skipped: sample size ${format.sampleSizeInBits}-bit not supported (requires 8/16/24/32-bit PCM)"
                }
            }
        }
    }

    private fun scaleSignedSamples(buffer: ByteArray, chunk: Int, bytesPerSample: Int, gain: Float, bigEndian: Boolean) {
        val limit = chunk - chunk % bytesPerSample
        val minValue = -(1L shl (bytesPerSample * 8 - 1))
        val maxValue = (1L shl (bytesPerSample * 8 - 1)) - 1L
        for (offset in 0 until limit step bytesPerSample) {
            val sample = readSignedSample(buffer, offset, bytesPerSample, bigEndian)
            val scaled = (sample * gain).toLong().coerceIn(minValue, maxValue)
            writeSignedSample(buffer, offset, bytesPerSample, scaled, bigEndian)
        }
    }

    private fun readSignedSample(buffer: ByteArray, offset: Int, bytesPerSample: Int, bigEndian: Boolean): Long {
        var value = 0L
        if (bigEndian) {
            for (i in 0 until bytesPerSample) {
                value = (value shl 8) or (buffer[offset + i].toLong() and 0xFF)
            }
        } else {
            for (i in bytesPerSample - 1 downTo 0) {
                value = (value shl 8) or (buffer[offset + i].toLong() and 0xFF)
            }
        }

        val bits = bytesPerSample * 8
        val signBit = 1L shl (bits - 1)
        val extensionMask = (-1L) shl bits
        return if (value and signBit != 0L) value or extensionMask else value
    }

    private fun writeSignedSample(buffer: ByteArray, offset: Int, bytesPerSample: Int, sample: Long, bigEndian: Boolean) {
        var value = sample
        if (bigEndian) {
            for (i in bytesPerSample - 1 downTo 0) {
                buffer[offset + i] = (value and 0xFF).toByte()
                value = value shr 8
            }
        } else {
            for (i in 0 until bytesPerSample) {
                buffer[offset + i] = (value and 0xFF).toByte()
                value = value shr 8
            }
        }
    }
}