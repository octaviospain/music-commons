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

package net.transgressoft.commons.fx.music

import net.transgressoft.commons.fx.music.audio.FXAudioLibrary
import net.transgressoft.commons.fx.music.audio.ObservableAlbum
import net.transgressoft.commons.fx.music.audio.ObservableArtistCatalog
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioLibrary
import net.transgressoft.commons.fx.music.audio.ObservableGenreIndex
import net.transgressoft.commons.fx.music.playlist.FXPlaylistHierarchy
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylist
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylistHierarchy
import net.transgressoft.commons.media.waveform.audioWaveformRepository
import net.transgressoft.commons.music.MusicLibrary
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.AudioMetadataIO
import net.transgressoft.commons.music.audio.JAudioTaggerMetadataIO
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.commons.music.itunes.ItunesImportService
import net.transgressoft.commons.music.m3u.M3uImportService
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.util.WindowsPathValidator
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.persistence.PersistentRepository
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlySetProperty
import mu.withLoggingContext
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * JavaFX-compatible entry point for managing an observable audio library, playlist hierarchy,
 * and waveform repository. Exposes JavaFX properties for direct binding to UI components.
 *
 * Composes [ObservableAudioLibrary], [ObservablePlaylistHierarchy], and an
 * [AudioWaveformRepository] and wires their event subscriptions so that audio item
 * changes propagate to dependent components automatically.
 *
 * Construct via [builder]:
 * ```kotlin
 * val library = FXMusicLibrary.builder()
 *     .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
 *     .playlistRepository(JsonFileRepository(playlistFile, ObservablePlaylistMapSerializer, loadOnInit = false))
 *     .build()
 * ```
 *
 * ## Thread safety — JavaFX Application Thread contract
 *
 * Observable property reads ([audioItemsProperty], [artistCatalogsProperty], [albumsProperty],
 * [genreIndexesProperty], [playlistsProperty]) and observable-collection mutations must occur on
 * the **JavaFX Application Thread**. Background CRUD events are routed to the FX thread
 * internally via `Platform.runLater` — callers do not need to wrap CRUD calls themselves.
 * [close] is safe to call from any thread; it is idempotent and uses `AtomicBoolean` to
 * ensure components are closed exactly once.
 *
 * @since 1.0
 */
public class FXMusicLibrary private constructor(
    private val _audioLibrary: FXAudioLibrary,
    private val _playlistHierarchy: FXPlaylistHierarchy,
    private val _waveformRepository: AudioWaveformRepository<AudioWaveform, ObservableAudioItem>,
    private val _metadataIO: AudioMetadataIO,
    /**
     * Diagnostic label for this library instance, used in MDC logging context.
     * @since 1.0
     */
    public val instanceName: String
) : MusicLibrary<ObservableAudioItem, ObservablePlaylist> {

    private val closed = AtomicBoolean(false)
    private val importLock = Any()

    /** Returns the underlying observable audio library for advanced operations. */
    override fun audioLibrary(): ObservableAudioLibrary = _audioLibrary

    /** Returns the underlying observable playlist hierarchy for advanced operations. */
    override fun playlistHierarchy(): ObservablePlaylistHierarchy = _playlistHierarchy

    /**
     * Returns the underlying waveform repository for advanced operations.
     * @since 1.0
     */
    public fun waveformRepository(): AudioWaveformRepository<AudioWaveform, ObservableAudioItem> = _waveformRepository

    private var _itunesImport: ItunesImportService<ObservableAudioItem, ObservablePlaylist>? = null

    /**
     * Accessor for the iTunes import service. Constructed on first access and cancelled on [close].
     *
     * @throws IllegalStateException if this library has already been closed.
     * @since 1.0
     */
    public val itunesImport: ItunesImportService<ObservableAudioItem, ObservablePlaylist>
        get() =
            synchronized(importLock) {
                check(!closed.get()) { "This music library has been closed and can no longer be used." }
                _itunesImport ?: ItunesImportService(this, _metadataIO, instanceName = instanceName).also { _itunesImport = it }
            }

    private var _m3uImport: M3uImportService<ObservableAudioItem, ObservablePlaylist>? = null

    /**
     * Accessor for the M3U import service. Constructed on first access and cancelled on [close].
     *
     * @throws IllegalStateException if this library has already been closed.
     * @since 1.0
     */
    public val m3uImport: M3uImportService<ObservableAudioItem, ObservablePlaylist>
        get() =
            synchronized(importLock) {
                check(!closed.get()) { "This music library has been closed and can no longer be used." }
                _m3uImport ?: M3uImportService(this, instanceName = instanceName).also { _m3uImport = it }
            }

    /**
     * Observable list of all audio items in the library, suitable for direct JavaFX binding.
     *
     * Must be read and bound on the **JavaFX Application Thread**. Background CRUD events are
     * coalesced and dispatched to the FX thread internally — no `Platform.runLater` wrapping
     * is required by the caller.
     *
     * @since 1.0
     */
    public val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> get() = _audioLibrary.audioItemsProperty

    /**
     * Boolean property that is `true` when the library contains no audio items.
     *
     * Must be read and bound on the **JavaFX Application Thread**.
     *
     * @since 1.0
     */
    public val emptyLibraryProperty: ReadOnlyBooleanProperty get() = _audioLibrary.emptyLibraryProperty

    /**
     * Observable set of all artist catalogs, each grouping albums and items by artist.
     *
     * Must be read and bound on the **JavaFX Application Thread**. Background CRUD events are
     * dispatched to the FX thread internally — no wrapping required by the caller.
     *
     * @since 1.0
     */
    public val artistCatalogsProperty: ReadOnlySetProperty<ObservableArtistCatalog> get() = _audioLibrary.artistCatalogsProperty

    /**
     * Observable ordered list of all album buckets, each grouping items by album.
     *
     * Must be read and bound on the **JavaFX Application Thread**.
     *
     * @since 1.0
     */
    public val albumsProperty: ReadOnlyListProperty<ObservableAlbum> get() = _audioLibrary.albumsProperty

    /**
     * Observable ordered list of all genre indexes, each grouping items by genre.
     *
     * Must be read and bound on the **JavaFX Application Thread**.
     *
     * @since 1.0
     */
    public val genreIndexesProperty: ReadOnlyListProperty<ObservableGenreIndex> get() = _audioLibrary.genreIndexesProperty

    /**
     * Observable set of all playlists in the hierarchy.
     *
     * Must be read and bound on the **JavaFX Application Thread**.
     *
     * @since 1.0
     */
    public val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist> get() = _playlistHierarchy.playlistsProperty

    /**
     * Subscriber that receives [AudioItemPlayerEvent] notifications.
     *
     * Subscribe an [net.transgressoft.commons.music.player.AudioItemPlayer] to this subscriber
     * so that play events increment the play count on the corresponding audio item.
     * @since 1.0
     */
    public val playerSubscriber: Flow.Subscriber<AudioItemPlayerEvent> get() = _audioLibrary.playerSubscriber

    /**
     * Publisher that emits artist catalog [CrudEvent] notifications.
     *
     * Subscribe to this publisher to receive updates when artist catalogs are created,
     * updated, or removed as audio items are added or deleted.
     * @since 1.0
     */
    public val artistCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Artist, ObservableArtistCatalog>>
        get() = _audioLibrary.artistCatalogPublisher

    /**
     * Creates an [ObservableAudioItem] from the audio file at [path] and adds it to the library.
     *
     * @param path the path to the audio file
     * @return the created [ObservableAudioItem]
     * @since 1.0
     */
    override fun audioItemFromFile(path: Path): ObservableAudioItem =
        withLoggingContext("libraryInstance" to instanceName) {
            WindowsPathValidator.validatePath(path)
            _audioLibrary.createFromFile(path)
        }

    override fun audioItemFromFile(path: Path, metadataTransform: (AudioItemMetadata) -> AudioItemMetadata): ObservableAudioItem =
        withLoggingContext("libraryInstance" to instanceName) {
            WindowsPathValidator.validatePath(path)
            _audioLibrary.createFromFile(path, metadataTransform)
        }

    override fun createPlaylist(name: String): ObservablePlaylist = _playlistHierarchy.createPlaylist(name)

    /**
     * Creates a new playlist with [name] pre-populated with [audioItems].
     *
     * @param name the unique playlist name
     * @param audioItems the audio items to include in the playlist
     * @return the created [ObservablePlaylist]
     * @throws IllegalArgumentException if a playlist with [name] already exists
     * @since 1.0
     */
    public fun createPlaylist(name: String, audioItems: List<ObservableAudioItem>): ObservablePlaylist =
        _playlistHierarchy.createPlaylist(name, audioItems)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createPlaylistWithIds")
    override fun createPlaylist(name: String, audioItemIds: List<Int>): ObservablePlaylist =
        _playlistHierarchy.createPlaylist(name, audioItemIds)

    override fun createPlaylistDirectory(name: String): ObservablePlaylist = _playlistHierarchy.createPlaylistDirectory(name)

    override fun movePlaylist(playlistNameToMove: String, destinationPlaylistName: String): Unit =
        _playlistHierarchy.movePlaylist(playlistNameToMove, destinationPlaylistName)

    override fun findPlaylistByName(name: String): Optional<out ObservablePlaylist> = _playlistHierarchy.findByName(name)

    /**
     * Closes all components idempotently. The first call cancels the import services first, then the
     * playlist hierarchy, waveform repository, and audio library. Subsequent calls return immediately
     * without re-closing the components.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(importLock) {
            _itunesImport?.close()
            _m3uImport?.close()
        }
        _playlistHierarchy.close()
        _waveformRepository.close()
        _audioLibrary.close()
    }

    /**
     * Builder for [FXMusicLibrary] that accepts a [Repository] implementation directly for each component.
     *
     * Each component defaults to a [VolatileRepository] (in-memory). Inject a different repository
     * implementation — `JsonFileRepository`, `SqlRepository`, or a custom one — to configure
     * persistence. JSON and SQL mappings for the library's entities are supplied by the opt-in
     * `music-commons-persistence-fx` module; add it (plus `lirp-sql` and a JDBC driver) to your
     * build only if you intend to back any component with a persistent repository.
     * @since 1.0
     */
    public class Builder {

        private var audioRepository: Repository<Int, ObservableAudioItem> = VolatileRepository("FXAudioLibrary")
        private var playlistRepository: Repository<Int, ObservablePlaylist> = VolatileRepository("FXPlaylistHierarchy")
        private var waveformRepository: Repository<Int, AudioWaveform> = VolatileRepository("FXWaveformRepository")
        private var metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()
        private var instanceName: String? = null

        /**
         * Injects the [repository] backing the observable audio library.
         *
         * To observe async persistence failures, wire a [net.transgressoft.lirp.event.LirpErrorHandler]
         * via the `onError` parameter when constructing the repository:
         * ```kotlin
         * JsonFileRepository(file, serializer, onError = LirpErrorHandler { throwable, ctx ->
         *     logger.error("${ctx.repository}/${ctx.operation} failed", throwable)
         * })
         * ```
         * The handler is notify-only and never receives entity field values — only identity
         * information (`LirpErrorContext.operation`, `entityIds`, `repository`).
         * Failure surfaces: [net.transgressoft.lirp.event.LirpOperation.FLUSH] (persistence write)
         * and [net.transgressoft.lirp.event.LirpOperation.EMIT] (event-channel drain).
         * @since 1.0
         */
        public fun audioRepository(repository: Repository<Int, ObservableAudioItem>): Builder =
            apply { audioRepository = repository }

        /**
         * Injects the [repository] backing the observable playlist hierarchy.
         *
         * To observe async persistence failures, wire a [net.transgressoft.lirp.event.LirpErrorHandler]
         * via the `onError` parameter when constructing the repository (same contract as
         * [audioRepository] — notify-only, identity-only context, no field values).
         * @since 1.0
         */
        public fun playlistRepository(repository: Repository<Int, ObservablePlaylist>): Builder =
            apply { playlistRepository = repository }

        /**
         * Injects the [repository] backing the waveform repository.
         *
         * To observe async persistence failures, wire a [net.transgressoft.lirp.event.LirpErrorHandler]
         * via the `onError` parameter when constructing the repository (same contract as
         * [audioRepository] — notify-only, identity-only context, no field values).
         * @since 1.0
         */
        public fun waveformRepository(repository: Repository<Int, AudioWaveform>): Builder =
            apply { waveformRepository = repository }

        /**
         * Injects the [AudioMetadataIO] used by the audio library for metadata I/O.
         *
         * Defaults to the JAudioTagger-backed implementation. Production callers rarely override
         * this — the seam exists primarily for tests that route reads/writes through a fake.
         * @since 1.0
         */
        public fun metadataIO(utils: AudioMetadataIO): Builder = apply { metadataIO = utils }

        /**
         * Sets a human-readable diagnostic label for this library instance.
         *
         * The name is surfaced as the `libraryInstance` MDC key around library operations and
         * import-service scopes, making logs attributable when multiple library instances are
         * active across time (e.g., sequential open/close cycles or concurrent imports).
         *
         * When not set, a short stable default is auto-generated from a per-JVM monotonic
         * counter (e.g., `music-library-1`, `music-library-2`). The name is the caller's
         * responsibility when set explicitly — it is a diagnostic label, not a trust boundary.
         *
         * @param name the label to assign; must be non-blank
         * @return this builder, for chaining
         * @since 1.0
         */
        public fun instanceName(name: String): Builder =
            apply {
                require(name.isNotBlank()) { "instanceName must be non-blank" }
                instanceName = name
            }

        /**
         * Builds the [FXMusicLibrary], wiring all event subscriptions between components.
         * @since 1.0
         */
        public fun build(): FXMusicLibrary {
            val resolvedName = instanceName ?: "fx-music-library-${instanceCounter.incrementAndGet()}"

            val audioLibrary = FXAudioLibrary(audioRepository, metadataIO)
            var playlistRepoRegistered = false
            var waveformRepo: AudioWaveformRepository<AudioWaveform, ObservableAudioItem>? = null
            var playlistHierarchy: FXPlaylistHierarchy? = null
            try {
                val audioItemSubscriber = AudioItemEventSubscriber<ObservableAudioItem>("AudioWaveformRepositorySubscriber")
                waveformRepo =
                    audioWaveformRepository<ObservableAudioItem>(
                        waveformRepository,
                        audioItemSubscriber
                    ) { audioItemSubscriber.cancelSubscription() }

                if (playlistRepository is PersistentRepository<*, *>) {
                    // Pre-register before load() so playlist entities can resolve audio-item aggregates
                    // through the context during deserialization. FXPlaylistHierarchy.init will call
                    // guardedRegister for the same repository reference — that call is a no-op because
                    // guardedRegister treats existing === repository as already-registered.
                    guardedRegister(ObservablePlaylist::class.java, playlistRepository)
                    playlistRepoRegistered = true
                    (playlistRepository as PersistentRepository<*, *>).load()
                }
                playlistHierarchy = FXPlaylistHierarchy(playlistRepository)

                // Flow.Subscriber overload — arms the async bridge for each dependent component;
                // not the lambda subscribe { } form and unaffected by the synchronous-by-default classification.
                audioLibrary.subscribe(waveformRepo)
                audioLibrary.subscribe(playlistHierarchy)

                return FXMusicLibrary(audioLibrary, playlistHierarchy, waveformRepo, metadataIO, resolvedName)
            } catch (ex: Exception) {
                if (playlistRepoRegistered) {
                    conditionalDeregister(ObservablePlaylist::class.java, playlistRepository)
                }
                playlistHierarchy?.close()
                waveformRepo?.close()
                audioLibrary.close()
                throw ex
            }
        }
    }

    public companion object {

        private val instanceCounter = AtomicInteger(0)

        /**
         * Returns a new [Builder] for constructing an [FXMusicLibrary].
         * @since 1.0
         */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}