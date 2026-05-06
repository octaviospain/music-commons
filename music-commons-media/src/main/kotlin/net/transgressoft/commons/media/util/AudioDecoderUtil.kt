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

import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.io.path.extension

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
    val baseFormat = rawStream.format

    // M4A/AAC special handling: JAAD SPI returns MP4AudioInputStream (raw AAC-encoded bytes).
    // AACFormatConversionProvider converts it to DecodedMP4AudioInputStream (decoded PCM)
    // when the target format matches channels, sample size, and endianness of the source.
    if (path.extension.equals("m4a", ignoreCase = true) ||
        baseFormat.encoding.toString().equals("AAC", ignoreCase = true)
    ) {
        val sampleSize = baseFormat.sampleSizeInBits.takeIf { it > 0 } ?: 16
        val pcmFormat =
            AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate,
                sampleSize,
                baseFormat.channels,
                baseFormat.channels * (sampleSize / 8),
                baseFormat.sampleRate,
                baseFormat.isBigEndian
            )
        // Triggers AACFormatConversionProvider → DecodedMP4AudioInputStream
        return AudioSystem.getAudioInputStream(pcmFormat, rawStream)
    }

    // Standard format conversion for WAV, FLAC, OGG, MP3 (via their respective SPIs)
    val targetFormat =
        AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            baseFormat.sampleRate,
            baseFormat.sampleSizeInBits.takeIf { it > 0 } ?: 16,
            baseFormat.channels,
            baseFormat.channels * 2,
            baseFormat.sampleRate,
            baseFormat.isBigEndian
        )
    return AudioSystem.getAudioInputStream(targetFormat, rawStream)
}