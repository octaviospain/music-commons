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

import net.transgressoft.commons.entity.ReactiveEntityBase
import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.AudioUtils.audioItemTrackDiscNumberComparator
import net.transgressoft.commons.music.audio.AudioFileType.FLAC
import net.transgressoft.commons.music.audio.AudioFileType.MP3
import net.transgressoft.commons.music.audio.AudioFileType.WAV
import com.neovisionaries.i18n.CountryCode
import mu.KotlinLogging
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
import org.jetbrains.kotlin.com.google.common.base.Objects
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.LocalDateTime
import kotlin.io.path.extension
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

const val UNASSIGNED_ID = 0

/**
 * Marker interface representing a concrete audio item implementation.
 *
 * Extends [ReactiveAudioItem] with self-referential type parameter to enable
 * type-safe operations while providing a non-generic entry point for audio item usage.
 */
interface AudioItem : ReactiveAudioItem<AudioItem>

/**
 * Mutable implementation of [AudioItem] that reads and writes audio file metadata.
 *
 * Reads metadata from audio files using JAudioTagger library and provides reactive
 * change notifications when metadata is modified. Supports asynchronous metadata
 * writing back to audio files while maintaining data integrity through thread-safe operations.
 *
 * The implementation automatically extracts metadata from the file on construction and
 * lazily caches immutable properties like duration and bitrate for performance.
 *
 * @see <a href=https://www.jthink.net/jaudiotagger/>JAudioTagger website</a>
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = AudioItemSerializer::class)
internal class MutableAudioItem(
    override val path: Path,
    override val id: Int = UNASSIGNED_ID
) : AudioItem, ReactiveEntityBase<Int, AudioItem>() {

    @Transient
    private val logger = KotlinLogging.logger {}

    private val ioScope =
        CoroutineScope(
            Dispatchers.IO + SupervisorJob() +
                CoroutineExceptionHandler { _, exception ->
                    val errorText = "Error writing metadata of $this"
                    logger.error(errorText, exception)
                }
        )

    // Constructor only for testing purposes
    internal constructor(audioItem: AudioItem) : this(audioItem.path, audioItem.id)

    // Constructor for deserialization
    internal constructor(
        path: Path,
        id: Int,
        title: String,
        duration: Duration,
        bitRate: Int,
        artist: Artist,
        album: Album,
        genre: Genre,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        encoding: String?,
        dateOfCreation: LocalDateTime,
        lastDateModified: LocalDateTime,
        playCount: Short
    ) : this(path, id) {
        this.title = title
        this._duration = duration
        this.artist = artist
        this.genre = genre
        this.comments = comments
        this.trackNumber = trackNumber
        this.discNumber = discNumber
        this.bpm = bpm
        this.album = album
        this._bitRate = bitRate
        this._encoder = encoder
        this._encoding = encoding
        this._dateOfCreation = dateOfCreation
        this.lastDateModified = lastDateModified
        this._playCount = playCount
    }

    @Transient
    private val audioFile = AudioFileIO.read(path.toFile())

    @Transient
    private val audioHeader = audioFile.audioHeader

    @Transient
    private val tag: Tag = audioFile.tag

    init {
        require(Files.exists(path)) { "File '${path.toAbsolutePath()}' does not exist" }
    }

    /** Immutable properties */

    private var _bitRate: Int = getBitRate(audioHeader)

    @Serializable
    override val bitRate: Int = _bitRate

    private var _duration: Duration = Duration.ofSeconds(audioHeader.trackLength.toLong())

    @Serializable
    override val duration: Duration = _duration

    private var _encoder: String? = getFieldIfExisting(tag, FieldKey.ENCODER)?.takeIf { it.isNotEmpty() }

    @Serializable
    override val encoder: String? = _encoder

    private var _encoding: String? = audioHeader.encodingType.takeIf { it.isNotEmpty() }

    @Serializable
    override val encoding: String? = _encoding

    private var _dateOfCreation: LocalDateTime = LocalDateTime.now()

    @Serializable
    override val dateOfCreation: LocalDateTime = _dateOfCreation

    @Serializable
    override var lastDateModified: LocalDateTime = _dateOfCreation

    private var _playCount: Short = 0
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    @Serializable
    override val playCount: Short
        get() = _playCount

    override val fileName by lazy {
        path.fileName.toString()
    }

    override val extension by lazy {
        path.extension
    }

    override val artistsInvolved
        get() = AudioUtils.getArtistsNamesInvolved(title, artist.name, album.albumArtist.name).map { ImmutableArtist.of(it) }.toSet()

    override val length by lazy {
        path.toFile().length()
    }

    override val uniqueId
        get() =
            buildString {
                append(path.fileName.toString().replace(' ', '_'))
                append("-$title")
                append("-${duration.toSeconds()}")
                append("-$bitRate")
            }

    /** Mutable properties */

    @Serializable
    override var title: String = getFieldIfExisting(tag, FieldKey.TITLE) ?: ""
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    @Serializable
    override var artist: Artist = readArtist(tag)
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    @Serializable
    override var genre: Genre = getFieldIfExisting(tag, FieldKey.GENRE)?.let { Genre.parseGenre(it) } ?: Genre.UNDEFINED
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    @Serializable
    override var comments: String? = getFieldIfExisting(tag, FieldKey.COMMENT)?.takeIf { it.isNotEmpty() }
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    @Serializable
    override var trackNumber: Short? = getFieldIfExisting(tag, FieldKey.TRACK)?.takeUnless { it.isEmpty().and(it == "0") }?.toShortOrNull()?.takeIf { it > 0 }
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    @Serializable
    override var discNumber: Short? = getFieldIfExisting(tag, FieldKey.DISC_NO)?.takeUnless { it.isEmpty().and(it == "0") }?.toShortOrNull()?.takeIf { it > 0 }
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    @Serializable
    override var bpm: Float? = getFieldIfExisting(tag, FieldKey.BPM)?.takeUnless { it.isEmpty().and(it == "0") }?.toFloatOrNull()?.takeIf { it > 0 }
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    @Serializable
    override var album: Album = readAlbum(tag, path.extension)
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    @Transient
    override var coverImageBytes: ByteArray? = getCoverBytes(tag)
        get() = field ?: getCoverBytes()
        set(value) {
            setAndNotify(value, field) { field = it }
        }

    private fun getFieldIfExisting(tag: Tag, fieldKey: FieldKey): String? = tag.hasField(fieldKey).takeIf { it }.run { tag.getFirst(fieldKey) }

    private fun readArtist(tag: Tag): Artist =
        getFieldIfExisting(tag, FieldKey.ARTIST)?.let { artistName ->
            val country =
                getFieldIfExisting(tag, FieldKey.COUNTRY)?.let { _country ->
                    if (_country.isNotEmpty()) CountryCode.valueOf(_country)
                    else CountryCode.UNDEFINED
                } ?: CountryCode.UNDEFINED
            ImmutableArtist.of(AudioUtils.beautifyArtistName(artistName), country)
        } ?: ImmutableArtist.UNKNOWN

    private fun readAlbum(tag: Tag, extension: String): ImmutableAlbum =
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

    private fun getBitRate(audioHeader: AudioHeader): Int {
        val bitRate = audioHeader.bitRate
        return if ("~" == bitRate.substring(0, 1)) {
            bitRate.substring(1).toInt()
        } else {
            bitRate.toInt()
        }
    }

    private fun getCoverBytes(): ByteArray? =
        path.toFile().let {
            if (it.exists() && it.canRead())
                getCoverBytes(AudioFileIO.read(it).tag)
            else null
        }

    private fun getCoverBytes(tag: Tag): ByteArray? = tag.artworkList.isNotEmpty().takeIf { it }?.let { tag.firstArtwork.binaryData }

    /**
     * Asynchronously writes the current metadata back to the audio file.
     *
     * Creates the appropriate tag format based on the file type and commits changes to disk.
     * Errors during writing are logged but do not throw exceptions to prevent
     * disrupting the application flow, especially during batch, background operations.
     */
    override fun writeMetadata(): Job =
        ioScope.launch {
            logger.debug { "Writing metadata of $this to file '${path.toAbsolutePath()}'" }
            val audioFile = path.toFile()
            val audio = AudioFileIO.read(audioFile)
            createTagTag(audio.audioHeader.format).let {
                audio.tag = it
            }

            audio.commit()
            logger.debug { "Metadata of $this successfully written to file" }
        }

    private fun createTagTag(format: String): Tag =
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

            else -> WavInfoTag()
        }.also {
            setTrackFieldsToTag(it)
        }

    private fun setTrackFieldsToTag(tag: Tag) {
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
            tag.addField(createArtwork(it))
        }
    }

    private fun createArtwork(coverBytes: ByteArray): Artwork {
        val tempCover: Path
        try {
            tempCover = Files.createTempFile("tempCover_$fileName", ".tmp")
            Files.write(tempCover, coverBytes, StandardOpenOption.CREATE)
            tempCover.toFile().deleteOnExit()
            return ArtworkFactory.createArtworkFromFile(tempCover.toFile())
        } catch (exception: IOException) {
            val errorText = "Error creating artwork of $this"
            logger.error(errorText, exception)
            throw AudioItemManipulationException(errorText, exception)
        }
    }

    internal fun incrementPlayCount() = _playCount++

    override operator fun compareTo(other: AudioItem) = audioItemTrackDiscNumberComparator<AudioItem>().compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as MutableAudioItem
        return trackNumber == that.trackNumber &&
            discNumber == that.discNumber &&
            bpm == that.bpm &&
            path == that.path &&
            title == that.title &&
            artist == that.artist &&
            album == that.album &&
            genre === that.genre &&
            comments == that.comments &&
            duration == that.duration
    }

    override fun hashCode() = Objects.hashCode(path, title, artist, album, genre, comments, trackNumber, discNumber, bpm, duration)

    override fun clone(): MutableAudioItem =
        MutableAudioItem(
            path,
            id,
            title,
            duration,
            bitRate,
            artist,
            album,
            genre,
            comments,
            trackNumber,
            discNumber,
            bpm,
            encoder,
            encoding,
            dateOfCreation,
            lastDateModified,
            playCount
        )

    override fun toString() = "AudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}