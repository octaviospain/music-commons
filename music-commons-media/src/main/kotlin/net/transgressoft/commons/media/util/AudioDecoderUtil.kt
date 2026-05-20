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

import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.ServiceLoader
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException
import javax.sound.sampled.spi.AudioFileReader

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
 *
 * The decoder probes every registered SPI reader end-to-end. A provider is skipped when
 * it crashes while opening the file, cannot convert the stream to PCM, or throws while
 * producing the first PCM bytes. That keeps a buggy reader from blocking a later one that
 * can actually play the file.
 */
fun decodeToPcmStream(path: Path): AudioInputStream {
    val file = path.toFile()
    val readers = prioritizeAudioFileReaders(file, loadAudioFileReaders())
    if (readers.isEmpty()) {
        throw UnsupportedAudioPlaybackException("No AudioFileReader providers registered for '${file.name}'")
    }

    return decodeWithReaders(file, readers)
}

private fun decodeWithReaders(file: File, readers: List<AudioFileReader>): AudioInputStream {
    var firstFailure: Throwable? = null
    for (reader in readers) {
        val openResult = openAudioInputStream(reader, file)
        if (openResult.failure != null && firstFailure == null) {
            firstFailure = openResult.failure
        }
        val rawStream = openResult.stream ?: continue

        val decodeResult = decodePcmStream(rawStream)
        if (decodeResult.failure != null && firstFailure == null) {
            firstFailure = decodeResult.failure
        }
        decodeResult.stream?.let { return it }
    }

    throw UnsupportedAudioPlaybackException(
        "Cannot decode '${file.name}': unsupported format (tried ${readers.size} providers)",
        firstFailure
    )
}

private fun openAudioInputStream(reader: AudioFileReader, file: File): ReaderAttempt =
    try {
        ReaderAttempt(reader.getAudioInputStream(file), null)
    } catch (_: UnsupportedAudioFileException) {
        ReaderAttempt(null, null)
    } catch (e: IOException) {
        ReaderAttempt(null, e)
    } catch (e: RuntimeException) {
        ReaderAttempt(null, e)
    }

private fun decodePcmStream(rawStream: AudioInputStream): ReaderAttempt {
    var pcmStream: AudioInputStream? = null
    var decodedStream: AudioInputStream? = null
    return try {
        pcmStream = convertToPcmStream(rawStream)
        decodedStream = validateReadablePcmStream(pcmStream)
        ReaderAttempt(decodedStream, null)
    } catch (_: UnsupportedAudioFileException) {
        ReaderAttempt(null, null)
    } catch (e: IOException) {
        ReaderAttempt(null, e)
    } catch (e: RuntimeException) {
        ReaderAttempt(null, e)
    } finally {
        if (decodedStream == null) {
            closeQuietly(pcmStream)
            closeQuietly(rawStream)
        }
    }
}

private fun convertToPcmStream(rawStream: AudioInputStream): AudioInputStream {
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

    return AudioSystem.getAudioInputStream(targetFormat, rawStream)
}

private fun validateReadablePcmStream(pcmStream: AudioInputStream): AudioInputStream {
    val frameSize = pcmStream.format.frameSize.takeIf { it > 0 } ?: 1
    val probeSize = maxOf(frameSize, 4096)
    val pushbackStream = java.io.PushbackInputStream(pcmStream, probeSize)
    val probe = ByteArray(probeSize)
    val bytesRead = pushbackStream.read(probe)
    if (bytesRead > 0) {
        pushbackStream.unread(probe, 0, bytesRead)
    }
    return AudioInputStream(pushbackStream, pcmStream.format, pcmStream.frameLength)
}

internal fun loadAudioFileReaders(): List<AudioFileReader> = ServiceLoader.load(AudioFileReader::class.java).toList()

internal fun prioritizeAudioFileReaders(file: File, readers: List<AudioFileReader>): List<AudioFileReader> =
    readers.sortedWith(compareBy<AudioFileReader> { readerPriority(file, it.javaClass.name) }.thenBy { it.javaClass.name })

internal fun readerPriority(file: File, readerClassName: String): Int {
    val extension = file.extension.lowercase()
    val className = readerClassName.lowercase()
    val preferredTokens =
        when (extension) {
            "mp3" -> listOf("mpeg", "mp3")
            "m4a", "mp4", "aac" -> listOf("aac", "mp4")
            "flac" -> listOf("flac")
            "ogg", "oga", "opus" -> listOf("vorbis", "ogg", "opus")
            "wav", "wave" -> listOf("wav", "wave")
            else -> emptyList()
        }

    val preferredIndex = preferredTokens.indexOfFirst { className.contains(it) }
    return if (preferredIndex >= 0) preferredIndex else preferredTokens.size
}

private fun closeQuietly(stream: AudioInputStream?) = runCatching { stream?.close() }

private data class ReaderAttempt(val stream: AudioInputStream?, val failure: Throwable?)