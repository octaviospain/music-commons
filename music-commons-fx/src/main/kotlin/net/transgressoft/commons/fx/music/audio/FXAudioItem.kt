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

import net.transgressoft.commons.fx.util.CoverLoadExecutor
import net.transgressoft.commons.fx.util.LazyObservationObjectProperty
import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.AudioMetadataIO
import net.transgressoft.commons.music.audio.Genre
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.extension
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
 * `metadataIO.loadCover(this)` and cache the result. Lifetimes are coterminous with the library:
 * items leave the library only by removal, which drops the library reference as well.
 *
 * Observing [coverImageProperty] — binding a listener or reading its value — triggers the lazy
 * cover load on first access when [metadataIO] is wired. UI code that binds an `ImageView` to
 * [coverImageProperty] therefore loads the cover on demand the moment the cell first displays the
 * track, without any explicit call to [coverImageBytes]. The property then propagates the decoded
 * [Image] to all bound listeners through the normal JavaFX notification path.
 *
 * [equals] / [hashCode] compare the cached cover-byte field directly rather than the lazy
 * [coverImageBytes] getter, so comparing or hashing an item never triggers the lazy cover load.
 */
class FXAudioItem
    @JvmOverloads
    internal constructor(
        override val path: Path,
        override val id: Int = UNASSIGNED_ID,
        metadata: AudioItemMetadata,
        override val dateOfCreation: LocalDateTime = LocalDateTime.now(),
        lastDateModified: LocalDateTime = dateOfCreation,
        playCount: Short = 0
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

        var metadata: AudioItemMetadata by reactiveProperty(metadata)

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

        @Transient
        override val dateOfCreationProperty: ReadOnlyObjectProperty<LocalDateTime> = SimpleObjectProperty(this, "date of creation", dateOfCreation)

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
                    append("-${duration.toSeconds()}")
                    append("-$bitRate")
                }

        /** Mutable properties — lirp-fx properties are the JavaFX source of truth.
         *  Change listeners on each lirp-fx property sync the domain setter, which publishes
         *  mutation events and updates lastDateModified. This pattern works both with and without
         *  a RegistryBase-managed repository. */

        @Transient
        override val titleProperty: StringProperty = fxString(metadata.title)

        @Transient
        private var _title: String = metadata.title

        override var title: String by reactiveProperty(
            getter = { _title },
            setter = { value ->
                _title = value
                syncArtistsInvolved()
                titleProperty.set(value)
            }
        )

        // LirpObjectProperty<T> extends SimpleObjectProperty<T?>, so it is ObjectProperty<T?> in
        // Kotlin's type system. The cast to ObjectProperty<T> (non-nullable) is safe at runtime
        // because JavaFX generics erase to raw types on the JVM.
        // Safe cast: generic type erased at runtime but guaranteed by the builder/serializer contract
        @Transient
        @Suppress("UNCHECKED_CAST")
        override val artistProperty: ObjectProperty<Artist> = fxObject(metadata.artist) as ObjectProperty<Artist>

        @Transient
        private var _artist: Artist = metadata.artist

        // Delegated reactive backing (over the private _artist field); the setter — which is also
        // the silent-hydration path — resyncs artistsInvolved and mirrors into artistProperty (the
        // JavaFX source of truth), while the delegate publishes the mutation event. The
        // artistProperty listener feeds external property edits back through this setter.
        override var artist: Artist by reactiveProperty(
            getter = { _artist },
            setter = { value ->
                _artist = value
                syncArtistsInvolved()
                artistProperty.set(value)
            }
        )

        @Transient
        @Suppress("UNCHECKED_CAST")
        override val albumProperty: ObjectProperty<AlbumDetails> = fxObject(metadata.album) as ObjectProperty<AlbumDetails>

        @Transient
        private var _album: AlbumDetails = metadata.album

        // Delegated reactive backing (over _album), mirroring into albumProperty (see artist above).
        override var album: AlbumDetails by reactiveProperty(
            getter = { _album },
            setter = { value ->
                _album = value
                syncArtistsInvolved()
                albumProperty.set(value)
            }
        )

        @Transient
        @Suppress("UNCHECKED_CAST")
        override val genresProperty: ObjectProperty<Set<Genre>> = fxObject(metadata.genres) as ObjectProperty<Set<Genre>>

        @Transient
        private var _genres: Set<Genre> = metadata.genres

        override var genres: Set<Genre> by reactiveProperty(
            getter = { _genres },
            setter = { value ->
                val copy = value.toSet()
                _genres = copy
                genresProperty.set(copy)
            }
        )

        @Transient
        override val commentsProperty: StringProperty by fxString(metadata.comments ?: "")

        @Transient
        private var _comments: String? = metadata.comments

        override var comments: String? by reactiveProperty(
            getter = { _comments },
            setter = { value ->
                _comments = value
                commentsProperty.set(value ?: "")
            }
        )

        @Transient
        override val trackNumberProperty: IntegerProperty by fxInteger(metadata.trackNumber?.toInt() ?: -1)

        @Transient
        private var _trackNumber: Short? = metadata.trackNumber

        override var trackNumber: Short? by reactiveProperty(
            getter = { _trackNumber },
            setter = { value ->
                _trackNumber = value
                trackNumberProperty.set(value?.toInt() ?: -1)
            }
        )

        @Transient
        override val discNumberProperty: IntegerProperty by fxInteger(metadata.discNumber?.toInt() ?: -1)

        @Transient
        private var _discNumber: Short? = metadata.discNumber

        override var discNumber: Short? by reactiveProperty(
            getter = { _discNumber },
            setter = { value ->
                _discNumber = value
                discNumberProperty.set(value?.toInt() ?: -1)
            }
        )

        @Transient
        override val bpmProperty: FloatProperty by fxFloat(metadata.bpm ?: -1f)

        @Transient
        private var _bpm: Float? = metadata.bpm

        override var bpm: Float? by reactiveProperty(
            getter = { _bpm },
            setter = { value ->
                value?.let { require(it.isFinite()) { "bpm must be a finite Float (got $it)" } }
                _bpm = value
                bpmProperty.set(value ?: -1f)
            }
        )

        @Serializable
        override var lastDateModified: LocalDateTime = lastDateModified
            set(value) {
                field = value
                lastDateModifiedProperty.set(value)
            }

        @Transient
        override val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime>
            field = SimpleObjectProperty(this, "date of last modification", lastDateModified)

        @Transient
        @Volatile
        private var _coverImageBytes: ByteArray? = metadata.coverBytes

        @Transient
        @Volatile
        private var coverLoaded: Boolean = metadata.coverBytes != null

        /**
         * Cover image bytes, loaded lazily through the wired [metadataIO] on first access.
         *
         * When [metadataIO] is null (orphan item, e.g. freshly-deserialized JSON entity before
         * the library rehydration pass attaches it), returns `null`. When set and the cache is
         * empty, fetches via `metadataIO.loadCover(this)`, caches, and fires a
         * [Platform.runLater] update to [coverImageProperty]. Subsequent reads return the cached
         * internal reference without re-invoking the IO seam. Callers must treat the returned
         * array as immutable and must not modify its contents.
         */
        @Transient
        override var coverImageBytes: ByteArray?
            get() {
                if (coverLoaded) return _coverImageBytes
                synchronized(this) {
                    if (!coverLoaded && metadataIO != null) {
                        _coverImageBytes = metadataIO?.loadCover(this)
                        coverLoaded = true
                        val bytes = _coverImageBytes
                        if (bytes != null) {
                            // Decode on the calling thread (the cover-load worker for the observe
                            // path) so only the property update — not the image decode — runs on the
                            // JavaFX thread.
                            val image = Image(ByteArrayInputStream(bytes))
                            runOnFxThread {
                                coverImageProperty.set(Optional.of(image))
                            }
                        }
                    }
                }
                return _coverImageBytes
            }
            set(value) {
                mutateAndPublish {
                    _coverImageBytes = value
                    coverLoaded = true
                    coverImageProperty.set(Optional.ofNullable(value).map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) })
                }
            }

        // UI code binds to coverImageProperty and never calls coverImageBytes directly, so the lazy
        // load would never fire through a plain SimpleObjectProperty. This subclass intercepts the
        // first observation (listener attach or value read) and delegates to coverImageBytes off the
        // observing thread, so binding the property on the JavaFX thread never blocks on disk I/O;
        // the resolved image is then published back onto the JavaFX thread.
        @Transient
        private val coverObservationTriggered = AtomicBoolean(coverLoaded)

        @Transient
        override val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>>
            field =
            LazyObservationObjectProperty(
                this@FXAudioItem,
                "cover image",
                Optional.ofNullable(_coverImageBytes)
                    .map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) },
                ::triggerLazyCoverLoad
            )

        private fun triggerLazyCoverLoad() {
            if (coverObservationTriggered.get()) return
            // Nothing can be loaded until metadataIO is wired (orphan item observed before the
            // library attaches the back-ref). Do not consume the trigger here, or a later
            // observation after wiring would be a permanent no-op and the cover would stay empty.
            if (!coverLoaded && metadataIO == null) return

            // Claim the trigger atomically so concurrent observations cannot both submit a load; a
            // non-atomic check-then-set would let more than one win and enqueue duplicate work.
            if (!coverObservationTriggered.compareAndSet(false, true)) return

            // Resolve off the JavaFX thread: the disk read and image decode run on the cover-load
            // worker, and the getter publishes coverImageProperty.set(...) back on the JavaFX thread.
            // Reset to coverLoaded once the load settles so a coverless-yet-unwired item retries after
            // metadataIO arrives, while a loaded (or known-coverless) item stays settled.
            CoverLoadExecutor.execute {
                try {
                    coverImageBytes
                } finally {
                    coverObservationTriggered.set(coverLoaded)
                }
            }
        }

        @Transient
        override val artistsInvolvedProperty: ReadOnlySetProperty<Artist> =
            SimpleSetProperty(
                this, "artists involved",
                FXCollections.observableSet(
                    getArtistsNamesInvolved(
                        titleProperty.value, artistProperty.value.name, albumProperty.value.albumArtist.name
                    ).map { Artist.of(it) }.toMutableSet()
                )
            )

        override val artistsInvolved: Set<Artist>
            get() = artistsInvolvedProperty.value

        @Serializable
        override val playCount: Short
            get() = playCountProperty.get().toShort()

        @Transient
        override val playCountProperty: ReadOnlyIntegerProperty
            field = SimpleIntegerProperty(this, "play count", playCount.toInt())

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
                ).map { Artist.of(it) }.toSet()
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

        override fun mutate(action: ObservableAudioItem.() -> Unit) = mutateAndPublish { action() }

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
                playCount == that.playCount
        }

        override fun hashCode() =
            Objects.hash(path, title, artist, album, genres, comments, trackNumber, discNumber, bpm, duration, playCount)

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
                    // Share the cover reference (safe under the immutable contract — callers must
                    // not mutate the array).
                    coverBytes = _coverImageBytes
                ),
                dateOfCreation,
                lastDateModified,
                playCount
            ).also {
                // Carry the lazy-load state so a clone of a not-yet-loaded coverable item can still
                // resolve its cover, and a clone of a known-coverless item does not re-probe disk.
                it.metadataIO = metadataIO
                it.coverLoaded = coverLoaded
            }

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