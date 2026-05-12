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

package net.transgressoft.commons.media.util

import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Decodes supported audio files to PCM_SIGNED data.
 *
 * Handles format-specific quirks:
 * - WAV/FLAC/OGG/MP3: JavaSound SPI format conversion works normally;
 *   sample size defaults to 16-bit when the SPI reports it as unknown
 *   (common for compressed formats like MP3 and OGG Vorbis).
 * - M4A/AAC: javasound-aac (JAAD) SPI returns an `MP4AudioInputStream` containing
 *   raw AAC-encoded bytes. The `AACFormatConversionProvider` converts this to
 *   `DecodedMP4AudioInputStream` (decoded PCM) when asked to convert AAC → PCM_SIGNED
 *   with matching channels, sample size, and endianness. We rely on this conversion
 *   path rather than reading raw bytes.
 */
fun decodeToPcmStream(path: Path): AudioInputStream {
    val rawStream = AudioSystem.getAudioInputStream(path.toFile())
    try {
        val baseFormat = rawStream.format
        val sampleSize = baseFormat.sampleSizeInBits.takeIf { it > 0 } ?: 16
        val targetFormat =
            AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate,
                sampleSize,
                baseFormat.channels,
                baseFormat.channels * (sampleSize / 8),
                baseFormat.sampleRate,
                baseFormat.isBigEndian
            )

        // M4A/AAC special handling: JAAD SPI returns MP4AudioInputStream (raw AAC-encoded bytes).
        // AACFormatConversionProvider converts it to DecodedMP4AudioInputStream (decoded PCM)
        // when the target format matches channels, sample size, and endianness of the source.
        // Standard format conversion otherwise covers WAV, FLAC, OGG, MP3 (via their respective SPIs)
        return AudioSystem.getAudioInputStream(targetFormat, rawStream)
    } catch (e: Throwable) {
        runCatching { rawStream.close() }
        throw e
    }
}