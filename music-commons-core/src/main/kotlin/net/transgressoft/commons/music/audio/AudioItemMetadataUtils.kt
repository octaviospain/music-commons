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

import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.audio.AudioFileType.FLAC
import net.transgressoft.commons.music.audio.AudioFileType.MP3
import net.transgressoft.commons.music.audio.AudioFileType.WAV
import com.neovisionaries.i18n.CountryCode
import mu.KLogger
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavInfoTag
import org.jaudiotagger.tag.wav.WavTag
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration

/**
 * Holds all metadata extracted from an audio file, using only plain Kotlin/Java types.
 *
 * Returned by [AudioItemMetadataUtils.readMetadata] so that audio item implementations
 * never need to depend on JAudioTagger types directly.
 */
data class AudioFileMetadata(
    val bitRate: Int,
    val duration: Duration,
    val encoder: String?,
    val encoding: String?,
    val title: String,
    val artist: Artist,
    val album: Album,
    val genre: Genre,
    val comments: String?,
    val trackNumber: Short?,
    val discNumber: Short?,
    val bpm: Float?,
    val coverBytes: ByteArray?
)

/**
 * Shared utility object centralizing all JAudioTagger metadata read and write operations.
 *
 * Isolates JAudioTagger-dependent code from [net.transgressoft.commons.music.audio.MutableAudioItem]
 * and [net.transgressoft.commons.fx.music.audio.FXAudioItem], creating a clean seam for future
 * library replacement. Both audio item implementations delegate metadata operations to this object
 * rather than depending on JAudioTagger types.
 *
 * The public API consists of [readMetadata], [readCoverBytes], and [writeMetadataToFile].
 * All JAudioTagger type references are confined to this object.
 */
object AudioItemMetadataUtils {

    /**
     * Reads all metadata from the audio file at [path] and returns it as an [AudioFileMetadata]
     * value object containing only plain Kotlin/Java types.
     *
     * @param path path to the audio file
     * @param extension file extension used to determine compilation flag parsing (m4a vs others)
     */
    fun readMetadata(path: Path, extension: String): AudioFileMetadata {
        val audioFile = AudioFileIO.read(path.toFile())
        val header = audioFile.audioHeader
        val tag = audioFile.tag
        return AudioFileMetadata(
            bitRate = parseBitRate(header),
            duration = Duration.ofSeconds(header.trackLength.toLong()),
            encoder = getFieldIfExisting(tag, FieldKey.ENCODER),
            encoding = header.encodingType,
            title = getFieldIfExisting(tag, FieldKey.TITLE) ?: "",
            artist = parseArtist(tag),
            album = parseAlbum(tag, extension),
            genre = getFieldIfExisting(tag, FieldKey.GENRE)?.let { Genre.parseGenre(it) } ?: Genre.UNDEFINED,
            comments = getFieldIfExisting(tag, FieldKey.COMMENT)?.takeIf { it.isNotEmpty() },
            trackNumber = parseOptionalShort(getFieldIfExisting(tag, FieldKey.TRACK)),
            discNumber = parseOptionalShort(getFieldIfExisting(tag, FieldKey.DISC_NO)),
            bpm = parseOptionalBpm(getFieldIfExisting(tag, FieldKey.BPM)),
            coverBytes = parseCoverBytes(tag)
        )
    }

    /**
     * Reads the cover image bytes from the audio file at [path], or returns `null` if the file
     * has no artwork or cannot be read.
     */
    fun readCoverBytes(path: Path): ByteArray? {
        val file = path.toFile()
        if (!file.exists() || !file.canRead()) return null
        return parseCoverBytes(AudioFileIO.read(file).tag)
    }

    /**
     * Writes metadata to the audio file at [path], creating the appropriate tag format and
     * committing changes to disk.
     *
     * @param logger caller's logger for artwork error reporting
     * @param fileName caller's file name for temp file naming during artwork creation
     */
    fun writeMetadataToFile(
        path: Path,
        title: String,
        album: Album,
        artist: Artist,
        genre: Genre,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        coverImageBytes: ByteArray?,
        fileName: String,
        logger: KLogger
    ) {
        val audio = AudioFileIO.read(path.toFile())
        createTag(
            audio.audioHeader.format, title, album, artist, genre,
            comments, trackNumber, discNumber, bpm, encoder,
            coverImageBytes, fileName, logger
        ).let {
            audio.tag = it
        }
        audio.commit()
    }

    private fun getFieldIfExisting(tag: Tag, fieldKey: FieldKey): String? =
        tag.hasField(fieldKey).takeIf { it }.run { tag.getFirst(fieldKey) }

    private fun parseArtist(tag: Tag): Artist =
        getFieldIfExisting(tag, FieldKey.ARTIST)?.let { artistName ->
            val country =
                getFieldIfExisting(tag, FieldKey.COUNTRY)?.let { _country ->
                    if (_country.isNotEmpty()) CountryCode.valueOf(_country)
                    else CountryCode.UNDEFINED
                } ?: CountryCode.UNDEFINED
            ImmutableArtist.of(AudioUtils.beautifyArtistName(artistName), country)
        } ?: ImmutableArtist.UNKNOWN

    private fun parseAlbum(tag: Tag, extension: String): ImmutableAlbum =
        getFieldIfExisting(tag, FieldKey.ALBUM).let { albumName ->
            return if (albumName == null) {
                ImmutableAlbum.UNKNOWN
            } else {
                val albumArtistName = getFieldIfExisting(tag, FieldKey.ALBUM_ARTIST) ?: ""
                val isCompilation =
                    getFieldIfExisting(tag, FieldKey.IS_COMPILATION)?.let {
                        if ("m4a" == extension) "1" == tag.getFirst(FieldKey.IS_COMPILATION)
                        else "true" == tag.getFirst(FieldKey.IS_COMPILATION)
                    } ?: false
                val year = getFieldIfExisting(tag, FieldKey.YEAR)?.toShortOrNull()?.takeIf { it > 0 }
                val label = getFieldIfExisting(tag, FieldKey.GROUPING)?.let { ImmutableLabel.of(it) } as Label
                ImmutableAlbum(albumName, ImmutableArtist.of(AudioUtils.beautifyArtistName(albumArtistName)), isCompilation, year, label)
            }
        }

    private fun parseBitRate(audioHeader: AudioHeader): Int {
        val bitRate = audioHeader.bitRate
        return if ("~" == bitRate.substring(0, 1)) {
            bitRate.substring(1).toInt()
        } else {
            bitRate.toInt()
        }
    }

    private fun parseCoverBytes(tag: Tag): ByteArray? = tag.artworkList.isNotEmpty().takeIf { it }?.let { tag.firstArtwork.binaryData }

    private fun parseOptionalShort(value: String?): Short? =
        value?.takeUnless { it.isEmpty().and(it == "0") }?.toShortOrNull()?.takeIf { it > 0 }

    private fun parseOptionalBpm(value: String?): Float? =
        value?.takeUnless { it.isEmpty().and(it == "0") }?.toFloatOrNull()?.takeIf { it > 0 }

    private fun createTag(
        format: String,
        title: String,
        album: Album,
        artist: Artist,
        genre: Genre,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        coverImageBytes: ByteArray?,
        fileName: String,
        logger: KLogger
    ): Tag =
        when {
            format.startsWith(WAV.extension, ignoreCase = true) -> {
                val wavTag = WavTag(WavOptions.READ_ID3_ONLY)
                wavTag.iD3Tag = ID3v24Tag()
                wavTag.infoTag = WavInfoTag()
                wavTag
            }

            format.startsWith(MP3.extension, ignoreCase = true) -> {
                TagOptionSingleton.getInstance().isWriteMp3GenresAsText = true
                val tag: Tag = ID3v24Tag()
                tag.artworkList.clear()
                tag
            }

            format.startsWith(FLAC.extension, ignoreCase = true) -> {
                val tag: Tag = FlacTag()
                tag.artworkList.clear()
                tag
            }

            format.startsWith("Aac", ignoreCase = true) -> {
                TagOptionSingleton.getInstance().isWriteMp4GenresAsText = true
                val tag: Tag = Mp4Tag()
                tag.artworkList.clear()
                tag
            }

            else -> {
                WavInfoTag()
            }
        }.also {
            setTrackFieldsToTag(it, title, album, artist, genre, comments, trackNumber, discNumber, bpm, encoder, coverImageBytes, fileName, logger)
        }

    private fun setTrackFieldsToTag(
        tag: Tag,
        title: String,
        album: Album,
        artist: Artist,
        genre: Genre,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        coverImageBytes: ByteArray?,
        fileName: String,
        logger: KLogger
    ) {
        tag.setField(FieldKey.TITLE, title)
        tag.setField(FieldKey.ALBUM, album.name)
        tag.setField(FieldKey.ALBUM_ARTIST, album.albumArtist.name)
        tag.setField(FieldKey.ARTIST, artist.name)
        tag.setField(FieldKey.GENRE, genre.capitalize())
        tag.setField(FieldKey.COUNTRY, artist.countryCode.name)
        comments?.let { tag.setField(FieldKey.COMMENT, it) }
        trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
        album.year?.let { tag.setField(FieldKey.YEAR, it.toString()) }
        tag.setField(FieldKey.ENCODER, encoder)
        tag.setField(FieldKey.GROUPING, album.label.name)
        discNumber?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }
        tag.setField(FieldKey.IS_COMPILATION, album.isCompilation.toString())
        bpm?.let {
            if (tag is Mp4Tag) {
                tag.setField(FieldKey.BPM, it.toInt().toString())
            } else {
                tag.setField(FieldKey.BPM, it.toString())
            }
        }
        coverImageBytes?.let {
            tag.deleteArtworkField()
            tag.addField(createArtwork(it, fileName, logger))
        }
    }

    private fun createArtwork(coverBytes: ByteArray, fileName: String, logger: KLogger): Artwork {
        val tempCover: Path
        try {
            tempCover = Files.createTempFile("tempCover_$fileName", ".tmp")
            Files.write(tempCover, coverBytes, StandardOpenOption.CREATE)
            tempCover.toFile().deleteOnExit()
            return ArtworkFactory.createArtworkFromFile(tempCover.toFile())
        } catch (exception: IOException) {
            val errorText = "Error creating artwork of $fileName"
            logger.error(errorText, exception)
            throw AudioItemManipulationException(errorText, exception)
        }
    }
}