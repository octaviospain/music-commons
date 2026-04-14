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

import net.transgressoft.commons.music.AudioUtils.audioItemTrackDiscNumberComparator
import net.transgressoft.commons.music.AudioUtils.getArtistsNamesInvolved
import net.transgressoft.commons.music.audio.AudioItemMetadataUtils.readMetadata
import net.transgressoft.commons.music.audio.AudioItemMetadataUtils.writeMetadataToFile
import net.transgressoft.lirp.entity.ReactiveEntityBase
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.Objects
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
interface AudioItem : ReactiveAudioItem<AudioItem> {

    override fun clone(): AudioItem
}

/**
 * Mutable implementation of [AudioItem] that reads and writes audio file metadata.
 *
 * Reads metadata from audio files using JAudioTagger library and provides reactive
 * change notifications when metadata is modified. Supports asynchronous metadata
 * writing back to audio files while maintaining data integrity through thread-safe operations.
 *
 * The implementation automatically extracts metadata from the file on construction and
 * lazily caches immutable properties like duration and bitrate for performance.
 * All JAudioTagger operations are delegated to [AudioItemMetadataUtils].
 *
 * @see <a href=https://www.jthink.net/jaudiotagger/>JAudioTagger website</a>
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE") // Serializer handles polymorphic AudioItem hierarchy; declared type intentionally broader than serializer target
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

    // Constructor for deserialization & iTunes import
    internal constructor(
        path: Path,
        id: Int,
        title: String,
        duration: Duration,
        bitRate: Int,
        artist: Artist,
        album: Album,
        genres: Set<Genre>,
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
        disableEvents()
        this.title = title
        this._duration = duration
        this.artist = artist
        this.genres = genres
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
        enableEvents()
    }

    init {
        require(Files.exists(path)) { "File '${path.toAbsolutePath()}' does not exist" }
    }

    @Transient
    private val metadata = readMetadata(path)

    private var _bitRate: Int = metadata.bitRate

    @Serializable
    override val bitRate: Int
        get() = _bitRate

    private var _duration: Duration = metadata.duration

    @Serializable
    override val duration: Duration
        get() = _duration

    private var _encoder: String? = metadata.encoder?.takeIf { it.isNotEmpty() }

    @Serializable
    override val encoder: String?
        get() = _encoder

    private var _encoding: String? = metadata.encoding?.takeIf { it.isNotEmpty() }

    @Serializable
    override val encoding: String?
        get() = _encoding

    private var _dateOfCreation: LocalDateTime = LocalDateTime.now()

    @Serializable
    override val dateOfCreation: LocalDateTime
        get() = _dateOfCreation

    @Serializable
    override var lastDateModified: LocalDateTime = _dateOfCreation

    private var _playCount: Short by reactiveProperty(0.toShort())

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
        get() = getArtistsNamesInvolved(title, artist.name, album.albumArtist.name).map { ImmutableArtist.of(it) }.toSet()

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
    override var title: String by reactiveProperty(metadata.title)

    @Serializable
    override var artist: Artist by reactiveProperty(metadata.artist)

    @Serializable
    override var genres: Set<Genre> by reactiveProperty(metadata.genres)

    @Transient
    private var _comments: String? = metadata.comments

    @Serializable
    override var comments: String?
        by reactiveProperty(
            getter = { _comments },
            setter = { _comments = it?.takeIf { s -> s.isNotEmpty() } }
        )

    @Serializable
    override var trackNumber: Short? by reactiveProperty(metadata.trackNumber)

    @Serializable
    override var discNumber: Short? by reactiveProperty(metadata.discNumber)

    @Serializable
    override var bpm: Float? by reactiveProperty(metadata.bpm)

    @Serializable
    override var album: Album by reactiveProperty(metadata.album)

    @Transient
    private var _coverImageBytes: ByteArray? = metadata.coverBytes?.copyOf()

    @Transient
    override var coverImageBytes: ByteArray?
        by reactiveProperty(
            getter = { _coverImageBytes?.copyOf() },
            setter = { _coverImageBytes = it?.copyOf() }
        )

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
            writeMetadataToFile(
                path, title, album, artist, genres,
                comments, trackNumber, discNumber, bpm, encoder,
                coverImageBytes, fileName, logger
            )
            logger.debug { "Metadata of $this successfully written to file" }
        }

    internal fun incrementPlayCount() = _playCount++

    override fun setPlayCount(count: Short) {
        require(count >= 0) { "Play count cannot be negative" }
        withEventsDisabled { _playCount = count }
    }

    override fun <T> withEventsSuppressed(action: () -> T): T {
        disableEvents()
        try {
            return action()
        } finally {
            enableEvents()
        }
    }

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
            genres == that.genres &&
            comments == that.comments &&
            duration == that.duration
    }

    override fun hashCode() = Objects.hash(path, title, artist, album, genres, comments, trackNumber, discNumber, bpm, duration)

    override fun clone(): MutableAudioItem =
        MutableAudioItem(
            path, id, title, duration, bitRate, artist, album, genres,
            comments, trackNumber, discNumber, bpm, encoder, encoding,
            dateOfCreation, lastDateModified, _playCount
        )

    override fun toString() = "AudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}