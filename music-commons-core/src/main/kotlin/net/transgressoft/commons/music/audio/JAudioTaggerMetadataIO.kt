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

import net.transgressoft.commons.music.audio.AudioFileType.FLAC
import net.transgressoft.commons.music.audio.AudioFileType.MP3
import net.transgressoft.commons.music.audio.AudioFileType.WAV
import net.transgressoft.commons.music.audio.joinGenres
import net.transgressoft.commons.music.audio.parseGenre
import com.neovisionaries.i18n.CountryCode
import mu.KotlinLogging
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotReadException
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
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration

/**
 * Production [AudioMetadataIO] backed by JAudioTagger.
 *
 * Encapsulates every JAudioTagger dependency behind the domain-typed [AudioMetadataIO] surface —
 * no `org.jaudiotagger.*` types ever escape this class. Fails fast when handed a [Path] from a
 * non-default filesystem provider (e.g. Jimfs): JAudioTagger reads through `java.io.File` and
 * non-default-FS paths throw `UnsupportedOperationException` from `path.toFile()`. Tests that
 * operate against an in-memory filesystem must inject the test-side `VolatileAudioMetadataIO`
 * instead — the guard surfaces the mismatch with a helpful message rather than the cryptic
 * upstream exception.
 *
 * Documented behavior: [TagOptionSingleton] is a process-wide mutable singleton shared by all
 * JAudioTagger operations. Its genre-text flags are set once at construction time. Constructing
 * multiple [JAudioTaggerMetadataIO] instances concurrently — or mutating [TagOptionSingleton]
 * from application code while write operations are in progress — can cause one format's settings
 * to be transiently observed by a concurrent write targeting a different format. For correct
 * behavior, create a single [JAudioTaggerMetadataIO] instance per JVM and do not mutate
 * [TagOptionSingleton] externally.
 *
 * Future Kotlin-native metadata libraries land alongside this class as additional
 * [AudioMetadataIO] implementations.
 */
class JAudioTaggerMetadataIO : AudioMetadataIO {

    private val logger = KotlinLogging.logger {}

    init {
        // JAudioTagger exposes genre-text flags only through a process-wide singleton. Set them
        // once at construction so concurrent writes to different formats don't race on these
        // toggles (previously the per-call createTag mutated them on every write, briefly making
        // an MP4 write observe MP3-only settings if an MP3 write was interleaved).
        TagOptionSingleton.getInstance().isWriteMp3GenresAsText = true
        TagOptionSingleton.getInstance().isWriteMp4GenresAsText = true
    }

    override fun readMetadata(path: Path): AudioItemMetadata {
        requireDefaultFileSystem(path)
        val tag =
            try {
                AudioFileIO.read(path.toFile()).tag
            } catch (_: CannotReadException) {
                return emptyItemMetadata(runCatching { readHeader(path) }.getOrNull())
            } catch (_: NullPointerException) {
                return emptyItemMetadata(runCatching { readHeader(path) }.getOrNull())
            }
        val header = readHeader(path)
        return AudioItemMetadata(
            title = getFieldIfExisting(tag, FieldKey.TITLE) ?: "",
            artist = parseArtist(tag),
            album = parseAlbum(tag),
            genres = getFieldIfExisting(tag, FieldKey.GENRE)?.let { parseGenre(it) } ?: emptySet(),
            comments = getFieldIfExisting(tag, FieldKey.COMMENT)?.takeIf { it.isNotEmpty() },
            trackNumber = parseOptionalShort(getFieldIfExisting(tag, FieldKey.TRACK)),
            discNumber = parseOptionalShort(getFieldIfExisting(tag, FieldKey.DISC_NO)),
            bpm = parseOptionalBpm(getFieldIfExisting(tag, FieldKey.BPM)),
            encoder = getFieldIfExisting(tag, FieldKey.ENCODER),
            encoding = header.encodingType,
            bitRate = header.bitRate,
            duration = Duration.ofSeconds(header.trackLengthSeconds)
        )
    }

    override fun loadCover(path: Path): ByteArray? {
        requireDefaultFileSystem(path)
        val file = path.toFile()
        if (!file.exists() || !file.canRead()) return null
        return runCatching { AudioFileIO.read(file).tag }
            .getOrNull()
            ?.let { tag ->
                if (tag.artworkList.isNotEmpty()) tag.firstArtwork.binaryData else null
            }
    }

    override fun writeMetadata(item: ReactiveAudioItem<*>) {
        requireDefaultFileSystem(item.path)
        val format = readHeader(item.path).format
        val tag =
            createTag(
                format = format,
                title = item.title,
                album = item.album,
                artist = item.artist,
                genres = item.genres,
                comments = item.comments,
                trackNumber = item.trackNumber,
                discNumber = item.discNumber,
                bpm = item.bpm,
                encoder = item.encoder,
                coverImageBytes = item.coverImageBytes,
                fileName = item.fileName
            )
        val audioFile = AudioFileIO.read(item.path.toFile())
        audioFile.tag = tag
        audioFile.commit()
    }

    private fun emptyItemMetadata(header: HeaderInfo?): AudioItemMetadata =
        AudioItemMetadata(
            bitRate = header?.bitRate ?: 0,
            duration = header?.let { Duration.ofSeconds(it.trackLengthSeconds) } ?: Duration.ZERO,
            encoding = header?.encodingType
        )

    private fun readHeader(path: Path): HeaderInfo {
        val header = AudioFileIO.read(path.toFile()).audioHeader
        return HeaderInfo(
            encodingType = header.encodingType,
            bitRate = parseBitRate(header.bitRate),
            trackLengthSeconds = header.trackLength.toLong(),
            format = header.format
        )
    }

    private fun parseBitRate(bitRate: String): Int = if (bitRate.startsWith("~")) bitRate.substring(1).toInt() else bitRate.toInt()

    private fun requireDefaultFileSystem(path: Path) {
        require(path.fileSystem == FileSystems.getDefault()) {
            "JAudioTaggerMetadataIO does not support non-default filesystems (got ${path.fileSystem}). " +
                "Use VolatileAudioMetadataIO for Jimfs paths."
        }
    }

    // getFirst returns "" for missing fields, but throws UnsupportedOperationException for some tag/field
    // combinations (e.g. WavTag + COUNTRY). Treat both as "field not present".
    private fun getFieldIfExisting(tag: Tag, fieldKey: FieldKey): String? = runCatching { tag.getFirst(fieldKey) }.getOrNull()

    private fun parseArtist(tag: Tag): Artist =
        getFieldIfExisting(tag, FieldKey.ARTIST)?.let { artistName ->
            val country =
                getFieldIfExisting(tag, FieldKey.COUNTRY)
                    ?.let { CountryCode.getByCode(it) ?: CountryCode.UNDEFINED }
                    ?: CountryCode.UNDEFINED
            Artist.of(beautifyArtistName(artistName), country)
        } ?: Artist.UNKNOWN

    private fun parseAlbum(tag: Tag): AlbumDetails =
        getFieldIfExisting(tag, FieldKey.ALBUM).let { albumName ->
            return if (albumName == null) {
                AlbumDetails.UNKNOWN
            } else {
                val albumArtistName = getFieldIfExisting(tag, FieldKey.ALBUM_ARTIST) ?: ""
                val isCompilation =
                    getFieldIfExisting(tag, FieldKey.IS_COMPILATION)?.let {
                        if (tag is Mp4Tag)
                            it == "1"
                        else
                            it == "true"
                    } ?: false
                val year = getFieldIfExisting(tag, FieldKey.YEAR)?.toShortOrNull()?.takeIf { it > 0 }
                val label = getFieldIfExisting(tag, FieldKey.GROUPING)?.let { Label.of(it) } ?: Label.UNKNOWN
                AlbumDetails(albumName, Artist.of(beautifyArtistName(albumArtistName)), isCompilation, year, label)
            }
        }

    private fun parseOptionalShort(value: String?): Short? = value?.takeUnless { it.isEmpty() || it == "0" }?.toShortOrNull()?.takeIf { it > 0 }

    private fun parseOptionalBpm(value: String?): Float? = value?.takeUnless { it.isEmpty() || it == "0" }?.toFloatOrNull()?.takeIf { it > 0 }

    @SuppressWarnings("kotlin:S107")
    private fun createTag(
        format: String,
        title: String,
        album: AlbumDetails,
        artist: Artist,
        genres: Set<Genre>,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        coverImageBytes: ByteArray?,
        fileName: String
    ): Tag =
        when {
            format.startsWith(WAV.extension, ignoreCase = true) -> {
                val wavTag = WavTag(WavOptions.READ_ID3_ONLY)
                wavTag.iD3Tag = ID3v24Tag()
                wavTag.infoTag = WavInfoTag()
                wavTag
            }

            format.startsWith(MP3.extension, ignoreCase = true) -> {
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
                val tag: Tag = Mp4Tag()
                tag.artworkList.clear()
                tag
            }

            else -> {
                WavInfoTag()
            }
        }.also {
            setTrackFieldsToTag(it, title, album, artist, genres, comments, trackNumber, discNumber, bpm, encoder, coverImageBytes, fileName)
        }

    @SuppressWarnings("kotlin:S107")
    private fun setTrackFieldsToTag(
        tag: Tag,
        title: String,
        album: AlbumDetails,
        artist: Artist,
        genres: Set<Genre>,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        coverImageBytes: ByteArray?,
        fileName: String
    ) {
        tag.setField(FieldKey.TITLE, title)
        tag.setField(FieldKey.ALBUM, album.name)
        tag.setField(FieldKey.ALBUM_ARTIST, album.albumArtist.name)
        tag.setField(FieldKey.ARTIST, artist.name)
        tag.setField(FieldKey.GENRE, joinGenres(genres))
        tag.setField(FieldKey.COUNTRY, artist.countryCode.name)
        comments?.let { tag.setField(FieldKey.COMMENT, it) }
        trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
        album.year?.let { tag.setField(FieldKey.YEAR, it.toString()) }
        encoder?.let { tag.setField(FieldKey.ENCODER, it) }
        tag.setField(FieldKey.GROUPING, album.label.name)
        discNumber?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }
        val compilationValue =
            if (tag is Mp4Tag) {
                if (album.isCompilation) "1" else "0"
            } else {
                album.isCompilation.toString()
            }
        tag.setField(FieldKey.IS_COMPILATION, compilationValue)
        bpm?.let {
            if (tag is Mp4Tag) {
                tag.setField(FieldKey.BPM, it.toInt().toString())
            } else {
                tag.setField(FieldKey.BPM, it.toString())
            }
        }
        coverImageBytes?.let {
            tag.deleteArtworkField()
            tag.addField(createArtwork(it, fileName))
        }
    }

    private fun createArtwork(coverBytes: ByteArray, fileName: String): Artwork {
        var tempCover: Path? = null
        try {
            tempCover = Files.createTempFile("tempCover_$fileName", ".tmp")
            Files.write(tempCover, coverBytes, StandardOpenOption.CREATE)
            return ArtworkFactory.createArtworkFromFile(tempCover.toFile())
        } catch (exception: IOException) {
            val errorText = "Error creating artwork of $fileName"
            logger.error(errorText, exception)
            throw AudioItemManipulationException(errorText, exception)
        } finally {
            tempCover?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }
}