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

package net.transgressoft.commons.music.audio

/**
 * Enumeration of audio codecs that can be detected within container formats.
 *
 * Some container formats (like M4A/MP4) can hold multiple different codecs.
 * This enum distinguishes between them for proper decoding and feature detection.
 *
 * @param isSupportedBySpi Whether a JavaSound SPI exists for this codec
 * @param defaultExtension The most common file extension for this codec
 */
enum class AudioFileCodec(
    val isSupportedBySpi: Boolean,
    val defaultExtension: String
) {
    /** MP3 codec (MPEG-1 Audio Layer III) */
    MP3(true, "mp3"),

    /** AAC codec (Advanced Audio Coding) in M4A container */
    AAC(true, "m4a"),

    /** ALAC codec (Apple Lossless) in M4A container — not yet supported */
    ALAC(false, "m4a"),

    /** PCM codec (Pulse Code Modulation) in WAV container */
    PCM(true, "wav"),

    /** FLAC codec (Free Lossless Audio Codec) */
    FLAC(true, "flac"),

    /** Vorbis codec in OGG container */
    VORBIS(true, "ogg"),

    /** Opus codec in OGG container — not yet supported */
    OPUS(false, "ogg");

    companion object {
        val supportedCodecs = entries.filter { it.isSupportedBySpi }
        val unsupportedCodecs = entries.filter { !it.isSupportedBySpi }
    }
}

/**
 * Enumeration of supported audio file types with their file extensions.
 *
 * Note: This enum represents container formats, not codecs. Some containers
 * (like M4A) can hold multiple codecs (AAC, ALAC). Use [AudioFileCodec] for
 * codec-specific detection when needed.
 */
enum class AudioFileType(
    val extension: String,
    val primaryCodec: AudioFileCodec,
    val possibleCodecs: List<AudioFileCodec>
) {
    MP3("mp3", AudioFileCodec.MP3, listOf(AudioFileCodec.MP3)),
    M4A("m4a", AudioFileCodec.AAC, listOf(AudioFileCodec.AAC, AudioFileCodec.ALAC)),
    WAV("wav", AudioFileCodec.PCM, listOf(AudioFileCodec.PCM)),
    FLAC("flac", AudioFileCodec.FLAC, listOf(AudioFileCodec.FLAC)),
    OGG("ogg", AudioFileCodec.VORBIS, listOf(AudioFileCodec.VORBIS, AudioFileCodec.OPUS));

    companion object {
        val extensions = entries.map { it.extension }

        /**
         * Returns the file type for a given extension.
         *
         * @param extension file extension without dot (e.g., "mp3", "m4a")
         * @return the matching AudioFileType or null if not supported
         */
        fun fromExtension(extension: String): AudioFileType? =
            entries.find { it.extension.equals(extension, ignoreCase = true) }
    }

    /**
     * Checks if a specific codec is possible for this file type.
     *
     * @param codec the codec to check
     * @return true if this codec can be contained in this file type
     */
    fun supportsCodec(codec: AudioFileCodec): Boolean = codec in possibleCodecs

    /**
     * Checks if the primary codec for this file type is supported by a JavaSound SPI.
     *
     * @return true if the primary codec has SPI support
     */
    fun isPrimaryCodecSupported(): Boolean = primaryCodec.isSupportedBySpi

    override fun toString(): String = extension
}

/**
 * Converts a file extension string to its corresponding [AudioFileType].
 *
 * @throws UnsupportedOperationException if the extension is not supported
 */
fun String.toAudioFileType(): AudioFileType =
    AudioFileType.fromExtension(this)
        ?: throw UnsupportedOperationException("'$this' is not a supported audio file extension")