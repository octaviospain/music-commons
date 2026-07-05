/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.media.util

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Root-mean-square amplitude of the big-endian 16-bit PCM [bytes], normalized to `[0, 1]`.
 * Valid decoded audio sits well below the ~0.577 white-noise ratio produced when raw compressed
 * bytes leak through undecoded; music typically lands in the 0.1–0.3 range.
 */
private fun pcmRmsRatio(bytes: ByteArray): Double {
    var rmsSq = 0.0
    val totalSamples = bytes.size / 2
    for (i in 0 until totalSamples) {
        val sample = ByteBuffer.wrap(bytes, i * 2, 2).order(ByteOrder.BIG_ENDIAN).short.toDouble()
        rmsSq += sample * sample
    }
    return sqrt(rmsSq / totalSamples) / Short.MAX_VALUE
}

private fun maxConsecutiveSampleJump(bytes: ByteArray, sampleCount: Int): Int {
    val samples =
        (0 until sampleCount).map { i ->
            ByteBuffer.wrap(bytes, i * 2, 2).order(ByteOrder.BIG_ENDIAN).short
        }
    return samples.zipWithNext { a, b -> abs(a.toInt() - b.toInt()) }.maxOrNull() ?: 0
}

internal class AacPcmQualityDiagnosticTest : FunSpec({

    context("decodeToPcmStream produces valid PCM (not raw compressed bytes)") {
        withData(
            mapOf(
                "AAC" to "testeable_aac.m4a",
                "AAC128" to "testeable_aac128.m4a"
            )
        ) { fixture ->
            val temp = resourceToTemp(fixture)
            try {
                val pcmStream = decodeToPcmStream(temp)
                val format = pcmStream.format

                assertSoftly {
                    format.encoding.toString() shouldBe "PCM_SIGNED"
                    format.sampleSizeInBits shouldBe 16
                    format.channels shouldBe 2
                    format.isBigEndian shouldBe true
                }

                val pcmBytes =
                    pcmStream.use { stream ->
                        val buf = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var bytesRead = stream.read(buffer)
                        while (bytesRead != -1) {
                            if (bytesRead > 0) buf.write(buffer, 0, bytesRead)
                            bytesRead = stream.read(buffer)
                        }
                        buf.toByteArray()
                    }

                // Valid audio has RMS ratio well below the ~0.577 white-noise floor of raw bytes.
                pcmRmsRatio(pcmBytes) shouldBeLessThan 0.5

                // Real audio has correlated consecutive samples, not wild min/max jumps near 65535.
                maxConsecutiveSampleJump(pcmBytes, sampleCount = 10).toDouble() shouldBeLessThan 40000.0
            } finally {
                deleteDecodedTempFile(temp)
            }
        }
    }
})