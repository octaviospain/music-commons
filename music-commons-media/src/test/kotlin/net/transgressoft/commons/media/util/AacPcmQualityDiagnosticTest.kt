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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class AacPcmQualityDiagnosticTest : StringSpec({

    fun resourceToTemp(name: String): java.nio.file.Path {
        val stream =
            javaClass.getResourceAsStream("/testfiles/$name")
                ?: throw IllegalArgumentException("Resource not found: $name")
        return stream.use {
            val temp = Files.createTempFile("audio_", ".${name.substringAfterLast('.')}")
            Files.copy(it, temp, StandardCopyOption.REPLACE_EXISTING)
            temp
        }
    }

    "decodeToPcmStream produces valid PCM for AAC (not raw AAC bytes)" {
        val temp = resourceToTemp("testeable_aac.m4a")
        try {
            val pcmStream = decodeToPcmStream(temp)
            val format = pcmStream.format

            format.encoding.toString() shouldBe "PCM_SIGNED"
            format.sampleSizeInBits shouldBe 16
            format.channels shouldBe 2
            format.isBigEndian shouldBe true

            // Read all PCM bytes
            val pcmBytes =
                pcmStream.use { stream ->
                    val buf = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var bytesRead = stream.read(buffer)
                    while (bytesRead != -1) {
                        if (bytesRead > 0) buf.write(buffer, 0, bytesRead)
                        bytesRead = stream.read(buffer)
                    }
                    buf.toByteArray()
                }

            println("=== PCM Quality Analysis (testeable_aac.m4a) ===")
            println("Total PCM bytes: ${pcmBytes.size}")
            println("First 32 bytes (hex): ${pcmBytes.take(32).joinToString(" ") { "%02x".format(it) }}")

            // Calculate RMS of PCM samples (big-endian, 16-bit)
            var rmsSq = 0.0
            val totalSamples = pcmBytes.size / 2
            for (i in 0 until totalSamples) {
                val idx = i * 2
                val sample = ByteBuffer.wrap(pcmBytes, idx, 2).order(ByteOrder.BIG_ENDIAN).short.toDouble()
                rmsSq += sample * sample
            }
            val rms = kotlin.math.sqrt(rmsSq / totalSamples)
            val rmsRatio = rms / Short.MAX_VALUE

            println("RMS: ${rms.toInt()}")
            println("RMS ratio: ${"%.4f".format(rmsRatio)}")
            println("White noise threshold: ~0.577 (uniform distribution)")

            // Valid audio should have RMS ratio well below white noise threshold
            // Typical music: 0.1-0.3, silence: <0.01
            // White noise (raw AAC bytes): ~0.57
            rmsRatio shouldBeLessThan 0.5

            // Also verify first few samples look like real audio (not random)
            val firstSamples =
                (0 until 10).map { i ->
                    ByteBuffer.wrap(pcmBytes, i * 2, 2).order(ByteOrder.BIG_ENDIAN).short
                }
            println("First 10 samples: ${firstSamples.joinToString(", ")}")

            // Real audio typically has consecutive samples that are correlated
            // (not jumping wildly between max and min values)
            val maxJump = firstSamples.zipWithNext { a, b -> kotlin.math.abs(a.toInt() - b.toInt()) }.maxOrNull() ?: 0
            println("Max jump between consecutive samples (first 10): $maxJump")

            // For real audio, jumps should be reasonable (not near 65535)
            maxJump.toDouble() shouldBeLessThan 40000.0
        } finally {
            deleteDecodedTempFile(temp)
        }
    }

    "decodeToPcmStream produces valid PCM for AAC128" {
        val temp = resourceToTemp("testeable_aac128.m4a")
        try {
            val pcmStream = decodeToPcmStream(temp)
            val format = pcmStream.format

            format.encoding.toString() shouldBe "PCM_SIGNED"
            format.sampleSizeInBits shouldBe 16
            format.channels shouldBe 2
            format.isBigEndian shouldBe true

            val pcmBytes =
                pcmStream.use { stream ->
                    val buf = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var bytesRead = stream.read(buffer)
                    while (bytesRead != -1) {
                        if (bytesRead > 0) buf.write(buffer, 0, bytesRead)
                        bytesRead = stream.read(buffer)
                    }
                    buf.toByteArray()
                }

            println("=== PCM Quality Analysis (testeable_aac128.m4a) ===")
            println("Total PCM bytes: ${pcmBytes.size}")
            println("First 32 bytes (hex): ${pcmBytes.take(32).joinToString(" ") { "%02x".format(it) }}")

            var rmsSq = 0.0
            val totalSamples = pcmBytes.size / 2
            for (i in 0 until totalSamples) {
                val idx = i * 2
                val sample = ByteBuffer.wrap(pcmBytes, idx, 2).order(ByteOrder.BIG_ENDIAN).short.toDouble()
                rmsSq += sample * sample
            }
            val rms = kotlin.math.sqrt(rmsSq / totalSamples)
            val rmsRatio = rms / Short.MAX_VALUE

            println("RMS: ${rms.toInt()}")
            println("RMS ratio: ${"%.4f".format(rmsRatio)}")

            rmsRatio shouldBeLessThan 0.5
        } finally {
            deleteDecodedTempFile(temp)
        }
    }
})