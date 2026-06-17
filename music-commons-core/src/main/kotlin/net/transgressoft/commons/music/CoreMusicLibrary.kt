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

package net.transgressoft.commons.music

import net.transgressoft.commons.media.waveform.audioWaveformRepository
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.ArtistCatalog
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioLibrary
import net.transgressoft.commons.music.audio.AudioMetadataIO
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.music.audio.JAudioTaggerMetadataIO
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.commons.music.playlist.DefaultPlaylistHierarchy
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.playlist.PlaylistHierarchy
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.util.WindowsPathValidator
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow

/**
 * Unified entry point for managing an audio library, playlist hierarchy, and waveform repository.
 *
 * Wraps [DefaultAudioLibrary], [DefaultPlaylistHierarchy], and the waveform repository with
 * builder-based repository injection, automatic event wiring, and lifecycle management.
 * Use [builder] to construct an instance and inject the [Repository] of your choice for each
 * component independently.
 *
 * ```kotlin
 * // In-memory (volatile) library:
 * val library = MusicLibrary.builder().build()
 *
 * // JSON-persisted library — consumers construct repositories directly:
 * val library = MusicLibrary.builder()
 *     .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
 *     .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
 *     .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
 *     .build()
 * ```
 */
class CoreMusicLibrary private constructor(
    private val _audioLibrary: AudioLibrary,
    private val _playlistHierarchy: PlaylistHierarchy,
    private val _waveformRepository: AudioWaveformRepository<AudioWaveform, AudioItem>
) : MusicLibrary<AudioItem, MutableAudioPlaylist> {

    override fun audioLibrary(): AudioLibrary = _audioLibrary

    override fun playlistHierarchy(): PlaylistHierarchy = _playlistHierarchy

    /**
     * Returns the underlying waveform repository for direct access to waveform management.
     */
    fun waveformRepository(): AudioWaveformRepository<AudioWaveform, AudioItem> = _waveformRepository

    /**
     * The subscriber for player events. Wire this to an audio player to track play counts.
     */
    val playerSubscriber: Flow.Subscriber<AudioItemPlayerEvent> get() = _audioLibrary.playerSubscriber

    /**
     * Publisher for artist catalog change events emitted by the audio library.
     */
    val artistCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Artist, ArtistCatalog<AudioItem>>>
        get() = _audioLibrary.artistCatalogPublisher

    override fun audioItemFromFile(path: Path): AudioItem {
        // Defensive boundary check at the public API entry point — MutableAudioItem.init validates
        // again, but failing here surfaces the exception before the audio item construction kicks off.
        WindowsPathValidator.validatePath(path)
        return _audioLibrary.createFromFile(path)
    }

    override fun createPlaylist(name: String): MutableAudioPlaylist = _playlistHierarchy.createPlaylist(name)

    /**
     * Creates a new playlist with [name] pre-populated with [audioItems].
     */
    fun createPlaylist(name: String, audioItems: List<AudioItem>): MutableAudioPlaylist =
        _playlistHierarchy.createPlaylist(name, audioItems)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createPlaylistWithIds")
    override fun createPlaylist(name: String, audioItemIds: List<Int>): MutableAudioPlaylist =
        _playlistHierarchy.createPlaylist(name, audioItemIds)

    override fun createPlaylistDirectory(name: String): MutableAudioPlaylist = _playlistHierarchy.createPlaylistDirectory(name)

    override fun movePlaylist(playlistNameToMove: String, destinationPlaylistName: String) =
        _playlistHierarchy.movePlaylist(playlistNameToMove, destinationPlaylistName)

    override fun findPlaylistByName(name: String): Optional<out MutableAudioPlaylist> = _playlistHierarchy.findByName(name)

    /**
     * Returns all audio items in [albumName] by [artist].
     */
    fun findAlbumAudioItems(artist: Artist, albumName: String): Collection<AudioItem> =
        _audioLibrary.findAlbumAudioItems(artist, albumName)

    /**
     * Returns the artist catalog for [artist], or an empty [Optional] if not found.
     */
    fun getArtistCatalog(artist: Artist): Optional<out ArtistCatalog<AudioItem>> = _audioLibrary.getArtistCatalog(artist)

    /**
     * Retrieves or creates a waveform asynchronously for [audioItem] with the given [width] and [height].
     */
    fun getOrCreateWaveformAsync(audioItem: AudioItem, width: Short, height: Short): CompletableFuture<AudioWaveform> =
        _waveformRepository.getOrCreateWaveformAsync(audioItem, width, height)

    /**
     * Subscribes [subscriber] to audio item CRUD events emitted by the audio library.
     *
     * Uses the `subscribe(Flow.Subscriber)` overload — a distinct Java-Flow-based overload that arms
     * the async bridge for the given subscriber. This is separate from the lambda-based
     * `subscribe { event -> ... }` overload and is not affected by the synchronous-by-default
     * classification that applies only to lambda subscriptions.
     */
    fun subscribeToAudioItemEvents(subscriber: Flow.Subscriber<CrudEvent<Int, AudioItem>>) =
        _audioLibrary.subscribe(subscriber)

    /**
     * Subscribes [subscriber] to playlist CRUD events emitted by the playlist hierarchy.
     *
     * Uses the `subscribe(Flow.Subscriber)` overload — a distinct Java-Flow-based overload, separate
     * from the lambda `subscribe { }` form. The synchronous-by-default classification does not apply;
     * no rename to `subscribeAsync` is needed.
     */
    fun subscribeToPlaylistEvents(subscriber: Flow.Subscriber<CrudEvent<Int, MutableAudioPlaylist>>) =
        _playlistHierarchy.subscribe(subscriber)

    /**
     * Closes all components in reverse subscription order: playlist hierarchy, waveform repository, then audio library.
     */
    override fun close() {
        _playlistHierarchy.close()
        _waveformRepository.close()
        _audioLibrary.close()
    }

    /**
     * Builder for [CoreMusicLibrary] that accepts a [Repository] implementation directly for each component.
     *
     * Each component defaults to a [VolatileRepository] (in-memory). Inject a different repository
     * implementation — `JsonFileRepository`, `SqlRepository`, or a custom one — to configure
     * persistence. The `lirp-sql` artifact and a JDBC driver are optional consumer dependencies:
     * add them to your build only if you intend to back any component with a SQL repository.
     *
     * The KSP-generated `MutableAudioItem_LirpTableDef` is a ready `SqlTableDef<AudioItem>`: its
     * `fromRow` rebuilds the entity and reconstructs the flyweight `Artist`/`Label` embeddables
     * through their `@PersistenceCreator` `of()` factories. Pass it straight to a `SqlRepository`/
     * `SqliteRepository`.
     *
     * ```kotlin
     * // consumer build.gradle — lirp-sql and a JDBC driver are consumer-side dependencies
     * // implementation("net.transgressoft:lirp-sql:<version>")
     * // implementation("org.xerial:sqlite-jdbc:<version>")
     *
     * import net.transgressoft.commons.music.audio.MutableAudioItem_LirpTableDef
     * import net.transgressoft.lirp.persistence.sql.SqliteRepository
     * import java.nio.file.Path
     *
     * val library = CoreMusicLibrary.builder()
     *     .audioRepository(SqliteRepository.fileBacked(Path.of("audio-library.db"), MutableAudioItem_LirpTableDef))
     *     .build()
     * ```
     */
    class Builder {

        private var audioRepository: Repository<Int, AudioItem> = VolatileRepository("AudioLibrary")
        private var playlistRepository: Repository<Int, MutableAudioPlaylist> = VolatileRepository("PlaylistHierarchy")
        private var waveformRepository: Repository<Int, AudioWaveform> = VolatileRepository("AudioWaveformRepository")
        private var metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()

        /**
         * Injects the [repository] backing the audio library.
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
         */
        fun audioRepository(repository: Repository<Int, AudioItem>): Builder = apply { audioRepository = repository }

        /**
         * Injects the [repository] backing the playlist hierarchy.
         *
         * To observe async persistence failures, wire a [net.transgressoft.lirp.event.LirpErrorHandler]
         * via the `onError` parameter when constructing the repository (same contract as
         * [audioRepository] — notify-only, identity-only context, no field values).
         */
        fun playlistRepository(repository: Repository<Int, MutableAudioPlaylist>): Builder =
            apply { playlistRepository = repository }

        /**
         * Injects the [repository] backing the waveform repository.
         *
         * To observe async persistence failures, wire a [net.transgressoft.lirp.event.LirpErrorHandler]
         * via the `onError` parameter when constructing the repository (same contract as
         * [audioRepository] — notify-only, identity-only context, no field values).
         */
        fun waveformRepository(repository: Repository<Int, AudioWaveform>): Builder =
            apply { waveformRepository = repository }

        /**
         * Injects the [AudioMetadataIO] used by the audio library for metadata I/O.
         *
         * Defaults to the JAudioTagger-backed implementation. Production callers rarely override
         * this — the seam exists primarily for tests that route reads/writes through a fake.
         */
        fun metadataIO(utils: AudioMetadataIO): Builder = apply { metadataIO = utils }

        /**
         * Builds a [CoreMusicLibrary] by injecting the configured repositories and wiring event
         * subscriptions automatically.
         *
         * Construction order matters: the audio library is created first so its repository is
         * registered in LirpContext before the playlist hierarchy resolves audio item references.
         * Rollback on partial failure closes everything constructed so far before rethrowing.
         */
        fun build(): CoreMusicLibrary {
            // 1. Audio library first — registers AudioItem in LirpContext
            val audioLibrary = DefaultAudioLibrary(audioRepository, metadataIO)

            var waveformRepo: AudioWaveformRepository<AudioWaveform, AudioItem>? = null
            var playlistHierarchy: DefaultPlaylistHierarchy? = null
            try {
                // 2. Waveform repository with audio item event subscriber
                val audioItemSubscriber = AudioItemEventSubscriber<AudioItem>("AudioWaveformRepositorySubscriber")
                waveformRepo = audioWaveformRepository(waveformRepository, audioItemSubscriber) { audioItemSubscriber.cancelSubscription() }

                // 3. Playlist hierarchy last — needs AudioItem already registered in LirpContext
                playlistHierarchy = DefaultPlaylistHierarchy(playlistRepository)

                // 4. Wire subscriptions so audio library events propagate to subscribers
                // Flow.Subscriber overload — arms the async bridge for each dependent component;
                // not the lambda subscribe { } form and unaffected by the synchronous-by-default classification.
                audioLibrary.subscribe(waveformRepo)
                audioLibrary.subscribe(playlistHierarchy)

                return CoreMusicLibrary(audioLibrary, playlistHierarchy, waveformRepo)
            } catch (ex: Exception) {
                (playlistHierarchy as? AutoCloseable)?.close()
                waveformRepo?.close()
                audioLibrary.close()
                throw ex
            }
        }
    }

    companion object {
        /**
         * Returns a new [Builder] to configure and construct a [CoreMusicLibrary].
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}