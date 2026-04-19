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

package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.AudioUtils.audioItemTrackDiscNumberComparator
import net.transgressoft.commons.music.AudioUtils.getArtistsNamesInvolved
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadataUtils.readMetadata
import net.transgressoft.commons.music.audio.AudioItemMetadataUtils.writeMetadataToFile
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.InvalidAudioFilePathException
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import net.transgressoft.commons.music.common.WindowsPathValidator
import net.transgressoft.lirp.entity.ReactiveEntityBase
import net.transgressoft.lirp.persistence.fx.fxFloat
import net.transgressoft.lirp.persistence.fx.fxInteger
import net.transgressoft.lirp.persistence.fx.fxObject
import net.transgressoft.lirp.persistence.fx.fxString
import javafx.beans.property.FloatProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleSetProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.scene.image.Image
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.Objects
import java.util.Optional
import kotlin.io.path.extension
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * JavaFX implementation of [ObservableAudioItem] with lirp-fx scalar properties.
 *
 * Each mutable metadata field is exposed as a lirp-fx property ([fxString], [fxObject],
 * [fxInteger], [fxFloat]) that serves as the single source of truth for its JavaFX value.
 * Reactive mutation events are published via [mutateAndPublish] so [lastDateModified] is
 * updated and subscribers are notified on every state change, both when properties are set
 * directly and when they are mutated through the lirp-fx JavaFX property.
 *
 * Side-effect logic (updating [artistsInvolvedProperty]) is registered as change listeners on
 * [titleProperty], [artistProperty], and [albumProperty] via [syncArtistsInvolved].
 *
 * [lastDateModifiedProperty] is updated automatically by [ReactiveEntityBase] after each
 * successful mutation.
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE") // Serializer handles polymorphic AudioItem hierarchy; declared type intentionally broader than serializer target
@Serializable(with = ObservableAudioItemSerializer::class)
class FXAudioItem internal constructor(
    override val path: Path,
    override val id: Int = UNASSIGNED_ID
): ObservableAudioItem, Comparable<ObservableAudioItem>,
    ReactiveEntityBase<Int, ObservableAudioItem>() {

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
    ): this(path, id) {
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
        this.coverImageBytes = metadata.coverBytes
        _playCountProperty.set(playCount.toInt())
        enableEvents()
    }

    init {
        WindowsPathValidator.validatePath(path)
        if (!Files.exists(path)) {
            throw InvalidAudioFilePathException("File '${path.toAbsolutePath()}' does not exist")
        }
        if (!Files.isRegularFile(path)) {
            throw InvalidAudioFilePathException("Path '${path.toAbsolutePath()}' is not a regular file")
        }
        if (!Files.isReadable(path)) {
            throw InvalidAudioFilePathException("File '${path.toAbsolutePath()}' is not readable")
        }
    }

    @Transient
    private val metadata = readMetadata(path)

    /** Immutable properties */

    private var _bitRate: Int = metadata.bitRate

    @Serializable
    override val bitRate: Int
        get() = _bitRate

    private var _duration: Duration = metadata.duration

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

    @Transient
    override val dateOfCreationProperty: ReadOnlyObjectProperty<LocalDateTime> = SimpleObjectProperty(this, "date of creation", _dateOfCreation)

    override val fileName by lazy {
        path.fileName.toString()
    }

    override val extension by lazy {
        path.extension
    }

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

    /** Mutable properties — lirp-fx properties are the JavaFX source of truth.
     *  Change listeners on each lirp-fx property sync the domain setter, which publishes
     *  mutation events and updates lastDateModified. This pattern works both with and without
     *  a RegistryBase-managed repository. */

    @Transient
    private val metadataTitle = metadata.title

    @Transient
    override val titleProperty: StringProperty = fxString(metadataTitle)

    override var title: String = metadataTitle
        set(value) {
            mutateAndPublish {
                field = value
                syncArtistsInvolved()
            }
            titleProperty.set(value)
        }

    // LirpObjectProperty<T> extends SimpleObjectProperty<T?>, so it is ObjectProperty<T?> in
    // Kotlin's type system. The cast to ObjectProperty<T> (non-nullable) is safe at runtime
    // because JavaFX generics erase to raw types on the JVM.
    // Safe cast: generic type erased at runtime but guaranteed by the builder/serializer contract
    @Transient
    private val metadataArtist: Artist = metadata.artist

    @Transient
    @Suppress("UNCHECKED_CAST")
    override val artistProperty: ObjectProperty<Artist> = fxObject(metadataArtist) as ObjectProperty<Artist>

    override var artist: Artist = metadataArtist
        set(value) {
            mutateAndPublish {
                field = value
                syncArtistsInvolved()
            }
            artistProperty.set(value)
        }

    @Transient
    private val metadataAlbum: Album = metadata.album

    @Transient
    @Suppress("UNCHECKED_CAST")
    override val albumProperty: ObjectProperty<Album> = fxObject(metadataAlbum) as ObjectProperty<Album>

    override var album: Album = metadataAlbum
        set(value) {
            mutateAndPublish {
                field = value
                syncArtistsInvolved()
            }
            albumProperty.set(value)
        }

    @Transient
    private val metadataGenres: Set<Genre> = metadata.genres.toSet()

    @Transient
    @Suppress("UNCHECKED_CAST")
    override val genresProperty: ObjectProperty<Set<Genre>> = fxObject(metadataGenres) as ObjectProperty<Set<Genre>>

    override var genres: Set<Genre> = metadataGenres
        set(value) {
            val copy = value.toSet()
            mutateAndPublish { field = copy }
            genresProperty.set(copy)
        }

    @Transient
    private val metadataComments: String? = metadata.comments

    @Transient
    override val commentsProperty: StringProperty by fxString(metadataComments ?: "")

    override var comments: String? = metadataComments
        set(value) {
            mutateAndPublish { field = value }
            commentsProperty.set(value ?: "")
        }

    @Transient
    private val metadataTrackNumber: Short? = metadata.trackNumber

    @Transient
    override val trackNumberProperty: IntegerProperty by fxInteger(metadataTrackNumber?.toInt() ?: -1)

    override var trackNumber: Short? = metadataTrackNumber
        set(value) {
            mutateAndPublish { field = value }
            trackNumberProperty.set(value?.toInt() ?: -1)
        }

    @Transient
    private val metadataDiscNumber: Short? = metadata.discNumber

    @Transient
    override val discNumberProperty: IntegerProperty by fxInteger(metadataDiscNumber?.toInt() ?: -1)

    override var discNumber: Short? = metadataDiscNumber
        set(value) {
            mutateAndPublish { field = value }
            discNumberProperty.set(value?.toInt() ?: -1)
        }

    @Transient
    private val metadataBpm: Float? = metadata.bpm

    @Transient
    override val bpmProperty: FloatProperty by fxFloat(metadataBpm ?: -1f)

    override var bpm: Float? = metadataBpm
        set(value) {
            mutateAndPublish { field = value }
            bpmProperty.set(value ?: -1f)
        }

    @Serializable
    override var lastDateModified: LocalDateTime = dateOfCreation
        set(value) {
            field = value
            _lastDateModifiedProperty.set(value)
        }

    private val _lastDateModifiedProperty = SimpleObjectProperty(this, "date of last modification", lastDateModified)

    @Transient
    override val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime> = _lastDateModifiedProperty

    override var coverImageBytes: ByteArray? = metadata.coverBytes?.copyOf()
        get() = field?.copyOf()
        set(value) {
            val copy = value?.copyOf()
            mutateAndPublish {
                field = copy
                _coverImageProperty.set(Optional.ofNullable(copy).map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) })
            }
        }

    private val _coverImageProperty =
        SimpleObjectProperty(
            this,
            "cover image",
            Optional.ofNullable(coverImageBytes)
                .map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) }
        )

    @Transient
    override val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>> = _coverImageProperty

    @Transient
    override val artistsInvolvedProperty: ReadOnlySetProperty<Artist> =
        SimpleSetProperty(
            this, "artists involved",
            FXCollections.observableSet(
                getArtistsNamesInvolved(
                    titleProperty.value, artistProperty.value.name, albumProperty.value.albumArtist.name
                ).map { ImmutableArtist.of(it) }.toMutableSet()
            )
        )

    override val artistsInvolved: Set<Artist>
        get() = artistsInvolvedProperty.value

    @Serializable
    override val playCount: Short
        get() = _playCountProperty.get().toShort()

    private val _playCountProperty = SimpleIntegerProperty(this, "play count", 0)

    @Transient
    override val playCountProperty: ReadOnlyIntegerProperty = _playCountProperty

    init {
        titleProperty.addListener { _, _, newTitle ->
            if (title != newTitle) title = newTitle
        }
        artistProperty.addListener { _, _, newArtist ->
            if (newArtist != null && artist != newArtist) artist = newArtist
        }
        albumProperty.addListener { _, _, newAlbum ->
            if (newAlbum != null && album != newAlbum) album = newAlbum
        }
        genresProperty.addListener { _, _, newGenres ->
            if (newGenres != null && genres != newGenres) genres = newGenres
        }
        commentsProperty.addListener { _, _, newComments ->
            val newValue = newComments.takeIf { it.isNotEmpty() }
            if (comments != newValue) comments = newValue
        }
        trackNumberProperty.addListener { _, _, newTrack ->
            val intVal = newTrack.toInt()
            val newValue = if (intVal in 0..Short.MAX_VALUE.toInt()) intVal.toShort() else null
            if (trackNumber != newValue) trackNumber = newValue
        }
        discNumberProperty.addListener { _, _, newDisc ->
            val intVal = newDisc.toInt()
            val newValue = if (intVal in 0..Short.MAX_VALUE.toInt()) intVal.toShort() else null
            if (discNumber != newValue) discNumber = newValue
        }
        bpmProperty.addListener { _, _, newBpm ->
            val newValue = newBpm.toFloat().takeUnless { it == -1f }
            if (bpm != newValue) bpm = newValue
        }
    }

    private fun syncArtistsInvolved() {
        val involved =
            getArtistsNamesInvolved(
                title, artist.name, album.albumArtist.name
            ).map { ImmutableArtist.of(it) }.toSet()
        artistsInvolvedProperty.clear()
        artistsInvolvedProperty.addAll(involved)
    }

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

    override fun setPlayCount(count: Short) {
        require(count >= 0) { "Play count cannot be negative" }
        withEventsDisabled { _playCountProperty.set(count.toInt()) }
    }

    internal fun incrementPlayCount() {
        mutateAndPublish {
            _playCountProperty.set(_playCountProperty.get() + 1)
        }
    }

    override operator fun compareTo(other: ObservableAudioItem) = audioItemTrackDiscNumberComparator<ObservableAudioItem>().compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as FXAudioItem
        return trackNumber == that.trackNumber &&
            discNumber == that.discNumber &&
            bpm == that.bpm &&
            path == that.path &&
            title == that.title &&
            artist == that.artist &&
            album == that.album &&
            genres == that.genres &&
            comments == that.comments &&
            duration == that.duration &&
            playCount == that.playCount &&
            coverImageBytes.contentEquals(that.coverImageBytes)
    }

    override fun hashCode() =
        Objects.hash(path, title, artist, album, genres, comments, trackNumber, discNumber, bpm, duration, playCount, coverImageBytes.contentHashCode())

    override fun clone(): FXAudioItem =
        FXAudioItem(
            path, id, title, duration, bitRate, artist, album, genres,
            comments, trackNumber, discNumber, bpm, encoder, encoding,
            dateOfCreation, lastDateModified, playCount
        )

    override fun toString() = "ObservableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}

val observableAudioItemSerializerModule =
    SerializersModule {
        polymorphic(ObservableAudioItem::class, ObservableAudioItemSerializer)
    }