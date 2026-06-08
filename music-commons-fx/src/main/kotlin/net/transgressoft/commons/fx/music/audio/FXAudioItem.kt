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

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.AudioMetadataIO
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import net.transgressoft.commons.music.audio.audioItemTrackDiscNumberComparator
import net.transgressoft.commons.music.audio.getArtistsNamesInvolved
import net.transgressoft.commons.util.WindowsPathValidator
import net.transgressoft.lirp.entity.ReactiveEntityBase
import net.transgressoft.lirp.persistence.fx.fxFloat
import net.transgressoft.lirp.persistence.fx.fxInteger
import net.transgressoft.lirp.persistence.fx.fxObject
import net.transgressoft.lirp.persistence.fx.fxString
import javafx.application.Platform
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
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.Objects
import java.util.Optional
import kotlin.io.path.extension
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

// Serializer is wired via `observableAudioItemSerializerModule` polymorphic registration (see bottom of this file).
// The serializer is a stateful class (ObservableAudioItemSerializer(FileSystem)) so it cannot be referenced as a
// bare `@Serializable(with = ...)` target — kotlinx-serialization requires KSerializer-typed ctor params.

/**
 * JavaFX implementation of [ObservableAudioItem] with lirp-fx scalar properties.
 *
 * Pure value holder: the primary constructor seeds reactive JavaFX properties from an
 * [AudioItemMetadata] value object. File-level concerns (existence checks, tag IO) live one layer up
 * in [FXAudioLibrary]; this class only validates the path shape via [WindowsPathValidator] and
 * exposes reactive mutators.
 *
 * Each mutable metadata field is exposed as a lirp-fx property ([fxString], [fxObject],
 * [fxInteger], [fxFloat]) that serves as the single source of truth for its JavaFX value.
 * Reactive mutation events are published via [mutateAndPublish] so [lastDateModified] is
 * updated and subscribers are notified on every state change.
 *
 * Cover image bytes are loaded lazily. The owning [FXAudioLibrary] is held as a strong back-ref
 * (wired by [FXAudioLibrary.add]) so [coverImageBytes] can fetch on first access through
 * `library.loadCover(this)` and cache the result. Lifetimes are coterminous with the library:
 * items leave the library only by removal, which drops the library reference as well.
 *
 * [equals] / [hashCode] read [coverImageBytes], which triggers the lazy load on first comparison
 * when a library back-ref is present. This deferred-IO contract is acceptable because callers
 * comparing freshly-deserialized items already accept the cost of resolving the path on disk.
 */
class FXAudioItem
    @JvmOverloads
    internal constructor(
        override val path: Path,
        override val id: Int = UNASSIGNED_ID,
        metadata: AudioItemMetadata
    ): ObservableAudioItem, Comparable<ObservableAudioItem>,
        ReactiveEntityBase<Int, ObservableAudioItem>() {

        /**
         * Metadata-IO back-ref used by the lazy [coverImageBytes] getter to load and cache the
         * cover image on demand. Wired by [FXAudioLibrary.add] when the item enters the library;
         * left `null` for orphan items (e.g. freshly-deserialized JSON entities before the library
         * rehydration pass attaches them) — in that case [coverImageBytes] returns `null`.
         */
        @Transient
        internal var metadataIO: AudioMetadataIO? = null

        // Constructor for deserialization & iTunes import
        internal constructor(
            path: Path,
            id: Int,
            metadata: AudioItemMetadata,
            dateOfCreation: LocalDateTime,
            lastDateModified: LocalDateTime,
            playCount: Short
        ): this(path, id, metadata) {
            disableEvents()
            this._dateOfCreation = dateOfCreation
            this.lastDateModified = lastDateModified
            playCountProperty.set(playCount.toInt())
            this.metadataIO = null
            enableEvents()
        }

        init {
            WindowsPathValidator.validatePath(path)
        }

        /** Immutable properties */

        @Serializable
        override val bitRate: Int = metadata.bitRate

        override val duration: Duration = metadata.duration

        @Serializable
        override val encoder: String? = metadata.encoder?.takeIf { it.isNotEmpty() }

        @Serializable
        override val encoding: String? = metadata.encoding?.takeIf { it.isNotEmpty() }

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
            Files.size(path)
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
        override val titleProperty: StringProperty = fxString(metadata.title)

        override var title: String = metadata.title
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
        @Suppress("UNCHECKED_CAST")
        override val artistProperty: ObjectProperty<Artist> = fxObject(metadata.artist) as ObjectProperty<Artist>

        override var artist: Artist = metadata.artist
            set(value) {
                mutateAndPublish {
                    field = value
                    syncArtistsInvolved()
                }
                artistProperty.set(value)
            }

        @Transient
        @Suppress("UNCHECKED_CAST")
        override val albumProperty: ObjectProperty<Album> = fxObject(metadata.album) as ObjectProperty<Album>

        override var album: Album = metadata.album
            set(value) {
                mutateAndPublish {
                    field = value
                    syncArtistsInvolved()
                }
                albumProperty.set(value)
            }

        @Transient
        @Suppress("UNCHECKED_CAST")
        override val genresProperty: ObjectProperty<Set<Genre>> = fxObject(metadata.genres) as ObjectProperty<Set<Genre>>

        override var genres: Set<Genre> = metadata.genres
            set(value) {
                val copy = value.toSet()
                mutateAndPublish { field = copy }
                genresProperty.set(copy)
            }

        @Transient
        override val commentsProperty: StringProperty by fxString(metadata.comments ?: "")

        override var comments: String? = metadata.comments
            set(value) {
                mutateAndPublish { field = value }
                commentsProperty.set(value ?: "")
            }

        @Transient
        override val trackNumberProperty: IntegerProperty by fxInteger(metadata.trackNumber?.toInt() ?: -1)

        override var trackNumber: Short? = metadata.trackNumber
            set(value) {
                mutateAndPublish { field = value }
                trackNumberProperty.set(value?.toInt() ?: -1)
            }

        @Transient
        override val discNumberProperty: IntegerProperty by fxInteger(metadata.discNumber?.toInt() ?: -1)

        override var discNumber: Short? = metadata.discNumber
            set(value) {
                mutateAndPublish { field = value }
                discNumberProperty.set(value?.toInt() ?: -1)
            }

        @Transient
        override val bpmProperty: FloatProperty by fxFloat(metadata.bpm ?: -1f)

        override var bpm: Float? = metadata.bpm
            set(value) {
                value?.let { require(it.isFinite()) { "bpm must be a finite Float (got $it)" } }
                mutateAndPublish { field = value }
                bpmProperty.set(value ?: -1f)
            }

        @Serializable
        override var lastDateModified: LocalDateTime = dateOfCreation
            set(value) {
                field = value
                lastDateModifiedProperty.set(value)
            }

        @Transient
        override val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime>
            field = SimpleObjectProperty(this, "date of last modification", lastDateModified)

        @Transient
        private var _coverImageBytes: ByteArray? = metadata.coverBytes?.copyOf()

        @Transient
        private var coverLoaded: Boolean = metadata.coverBytes != null

        /**
         * Cover image bytes, loaded lazily through the wired [metadataIO] on first access.
         *
         * When [metadataIO] is null (orphan item, e.g. freshly-deserialized JSON entity before
         * the library rehydration pass attaches it), returns `null`. When set and the cache is
         * empty, fetches via `metadataIO.loadCover(this)`, caches, and fires a
         * [Platform.runLater] update to [coverImageProperty]. Subsequent reads return the cached
         * defensive copy without re-invoking the IO seam.
         */
        @Transient
        override var coverImageBytes: ByteArray?
            get() {
                if (!coverLoaded && metadataIO != null) {
                    _coverImageBytes = metadataIO?.loadCover(this)
                    coverLoaded = true
                    val bytes = _coverImageBytes
                    if (bytes != null) {
                        runOnFxThread {
                            coverImageProperty.set(Optional.of(Image(ByteArrayInputStream(bytes))))
                        }
                    }
                }
                return _coverImageBytes?.copyOf()
            }
            set(value) {
                val copy = value?.copyOf()
                mutateAndPublish {
                    _coverImageBytes = copy
                    coverLoaded = true
                    coverImageProperty.set(Optional.ofNullable(copy).map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) })
                }
            }

        @Transient
        override val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>>
            field =
            SimpleObjectProperty(
                this,
                "cover image",
                Optional.ofNullable(_coverImageBytes)
                    .map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) }
            )

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
            get() = playCountProperty.get().toShort()

        @Transient
        override val playCountProperty: ReadOnlyIntegerProperty
            field = SimpleIntegerProperty(this, "play count", 0)

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

        override fun setPlayCount(count: Short) {
            require(count >= 0) { "Play count cannot be negative" }
            withEventsDisabled { playCountProperty.set(count.toInt()) }
        }

        internal fun incrementPlayCount() {
            mutateAndPublish {
                playCountProperty.set(playCountProperty.get() + 1)
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
                path,
                id,
                AudioItemMetadata(
                    title = title,
                    artist = artist,
                    album = album,
                    genres = genres,
                    comments = comments,
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    bpm = bpm,
                    encoder = encoder,
                    encoding = encoding,
                    bitRate = bitRate,
                    duration = duration,
                    // Seed the clone's cover cache with the current bytes so equals() in
                    // mutateAndPublish's pre/post comparison sees the same state without triggering
                    // a lazy load on either side.
                    coverBytes = _coverImageBytes?.copyOf()
                ),
                dateOfCreation,
                lastDateModified,
                playCount
            )

        override fun toString() = "ObservableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"

        // Runs the action on the JavaFX application thread when the toolkit is initialized; otherwise
        // executes inline. This keeps the lazy cover-load path resilient when invoked from a headless
        // context (e.g. unit tests that exercise the FXAudioItem getter without a toolkit).
        private fun runOnFxThread(action: () -> Unit) {
            try {
                Platform.runLater(action)
            } catch (_: IllegalStateException) {
                action()
            }
        }
    }

/**
 * Kotlinx [SerializersModule] registering the polymorphic subtype consumed by
 * [ObservableAudioItemMapSerializer] when round-tripping FX audio-library JSON.
 *
 * Registers `ObservableAudioItem` → [ObservableAudioItemSerializer] (which materializes
 * deserialized entries as [FXAudioItem] with their JavaFX property bindings reconstructed).
 *
 * Pass this module as `serializersModule` when constructing a `Json` instance manually:
 *
 * ```
 * val json = Json { serializersModule = observableAudioItemSerializerModule }
 * ```
 *
 * `JsonFileRepository(audioFile, ObservableAudioItemMapSerializer)` registers this module
 * automatically, so consumers using the convenience repository do not need to touch it.
 *
 * Thread-safety: immutable; safe to share across threads.
 *
 * @see ObservableAudioItemMapSerializer
 */
val observableAudioItemSerializerModule =
    SerializersModule {
        polymorphic(ObservableAudioItem::class, ObservableAudioItemSerializer())
    }