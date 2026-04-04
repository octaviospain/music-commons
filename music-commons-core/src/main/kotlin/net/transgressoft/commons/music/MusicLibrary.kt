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

import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.ArtistCatalog
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.music.audio.AudioLibrary
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.commons.music.playlist.DefaultPlaylistHierarchy
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.playlist.PlaylistHierarchy
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.music.waveform.DefaultAudioWaveformRepository
import net.transgressoft.lirp.entity.IdentifiableEntity
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.sql.SqlRepository
import net.transgressoft.lirp.persistence.sql.SqlTableDef
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import javax.sql.DataSource

internal sealed interface StorageConfig

internal data object VolatileStorage : StorageConfig

internal data class JsonFileStorage(val file: java.io.File) : StorageConfig

internal data class SqlStorage<E : IdentifiableEntity<*>>(
    val dataSource: DataSource,
    val tableDef: SqlTableDef<E>
) : StorageConfig

/**
 * Unified entry point for managing an audio library, playlist hierarchy, and waveform repository.
 *
 * Wraps [DefaultAudioLibrary], [DefaultPlaylistHierarchy], and [DefaultAudioWaveformRepository]
 * with builder-based storage configuration, automatic event wiring, and lifecycle management.
 * Use [builder] to construct an instance and configure persistence for each component independently.
 *
 * ```kotlin
 * // In-memory (volatile) library:
 * val library = MusicLibrary.builder().build()
 *
 * // JSON-persisted library:
 * val library = MusicLibrary.builder()
 *     .audioLibraryJsonFile(audioFile)
 *     .playlistHierarchyJsonFile(playlistsFile)
 *     .waveformRepositoryJsonFile(waveformsFile)
 *     .build()
 * ```
 */
class MusicLibrary private constructor(
    private val _audioLibrary: AudioLibrary<AudioItem, ArtistCatalog<AudioItem>>,
    private val _playlistHierarchy: PlaylistHierarchy<AudioItem, MutableAudioPlaylist>,
    private val _waveformRepository: AudioWaveformRepository<AudioWaveform, AudioItem>
) : AutoCloseable {

    /**
     * Returns the underlying audio library for direct access to audio item management.
     */
    fun audioLibrary(): AudioLibrary<AudioItem, ArtistCatalog<AudioItem>> = _audioLibrary

    /**
     * Returns the underlying playlist hierarchy for direct access to playlist management.
     */
    fun playlistHierarchy(): PlaylistHierarchy<AudioItem, MutableAudioPlaylist> = _playlistHierarchy

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

    /**
     * Creates an [AudioItem] from the audio file at [path] and adds it to the audio library.
     */
    fun audioItemFromFile(path: Path): AudioItem = _audioLibrary.createFromFile(path)

    /**
     * Creates a new playlist with [name] and adds it to the playlist hierarchy.
     */
    fun createPlaylist(name: String): MutableAudioPlaylist = _playlistHierarchy.createPlaylist(name)

    /**
     * Creates a new playlist with [name] pre-populated with [audioItems].
     */
    fun createPlaylist(name: String, audioItems: List<AudioItem>): MutableAudioPlaylist =
        _playlistHierarchy.createPlaylist(name, audioItems)

    /**
     * Creates a new playlist directory with [name].
     */
    fun createPlaylistDirectory(name: String): MutableAudioPlaylist = _playlistHierarchy.createPlaylistDirectory(name)

    /**
     * Moves the playlist identified by [playlistNameToMove] into the directory identified by [destinationPlaylistName].
     */
    fun movePlaylist(playlistNameToMove: String, destinationPlaylistName: String) =
        _playlistHierarchy.movePlaylist(playlistNameToMove, destinationPlaylistName)

    /**
     * Finds a playlist by its [name], returning an empty [Optional] if not found.
     */
    fun findPlaylistByName(name: String): Optional<out MutableAudioPlaylist> = _playlistHierarchy.findByName(name)

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
     */
    fun subscribeToAudioItemEvents(subscriber: Flow.Subscriber<CrudEvent<Int, AudioItem>>) =
        _audioLibrary.subscribe(subscriber)

    /**
     * Subscribes [subscriber] to playlist CRUD events emitted by the playlist hierarchy.
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
     * Builder for [MusicLibrary] that configures storage for each component independently.
     *
     * By default all components use in-memory (volatile) storage. Call the appropriate
     * `*JsonFile` or `*Sql` method to configure persistence for each component:
     *
     * ```kotlin
     * val library = MusicLibrary.builder()
     *     .audioLibrarySql(dataSource, audioTableDef)
     *     .playlistHierarchySql(dataSource, playlistTableDef)
     *     .waveformRepositorySql(dataSource, waveformTableDef)
     *     .build()
     * ```
     */
    class Builder {

        private var audioLibraryStorage: StorageConfig = VolatileStorage
        private var playlistHierarchyStorage: StorageConfig = VolatileStorage
        private var waveformRepositoryStorage: StorageConfig = VolatileStorage

        /** Configures the audio library to persist to [file] using JSON. */
        fun audioLibraryJsonFile(file: java.io.File): Builder = apply { audioLibraryStorage = JsonFileStorage(file) }

        /** Configures the playlist hierarchy to persist to [file] using JSON. */
        fun playlistHierarchyJsonFile(file: java.io.File): Builder =
            apply { playlistHierarchyStorage = JsonFileStorage(file) }

        /** Configures the waveform repository to persist to [file] using JSON. */
        fun waveformRepositoryJsonFile(file: java.io.File): Builder =
            apply { waveformRepositoryStorage = JsonFileStorage(file) }

        /**
         * Configures the audio library to persist to a SQL database using [dataSource] and [tableDef].
         *
         * @param dataSource the JDBC data source for database connections
         * @param tableDef the KSP-generated table definition describing [AudioItem] column mapping
         */
        fun audioLibrarySql(dataSource: DataSource, tableDef: SqlTableDef<AudioItem>): Builder =
            apply { audioLibraryStorage = SqlStorage(dataSource, tableDef) }

        /**
         * Configures the playlist hierarchy to persist to a SQL database using [dataSource] and [tableDef].
         *
         * @param dataSource the JDBC data source for database connections
         * @param tableDef the KSP-generated table definition describing [MutableAudioPlaylist] column mapping
         */
        fun playlistHierarchySql(dataSource: DataSource, tableDef: SqlTableDef<MutableAudioPlaylist>): Builder =
            apply { playlistHierarchyStorage = SqlStorage(dataSource, tableDef) }

        /**
         * Configures the waveform repository to persist to a SQL database using [dataSource] and [tableDef].
         *
         * @param dataSource the JDBC data source for database connections
         * @param tableDef the KSP-generated table definition describing [AudioWaveform] column mapping
         */
        fun waveformRepositorySql(dataSource: DataSource, tableDef: SqlTableDef<AudioWaveform>): Builder =
            apply { waveformRepositoryStorage = SqlStorage(dataSource, tableDef) }

        /**
         * Builds a [MusicLibrary] with the configured storage, wiring event subscriptions automatically.
         *
         * Construction order matters: the audio library is created first so its repository is
         * registered in LirpContext before the playlist hierarchy resolves audio item references.
         */
        fun build(): MusicLibrary {
            // 1. Audio library first — registers AudioItem in LirpContext
            val audioRepo = createAudioRepository()
            val audioLibrary = DefaultAudioLibrary(audioRepo)

            // 2. Waveform repository
            val waveformRepo = createWaveformRepository()
            val waveformRepository = DefaultAudioWaveformRepository<AudioItem>(waveformRepo)

            // 3. Playlist hierarchy last — needs AudioItem already registered in LirpContext
            val playlistHierarchy = createPlaylistHierarchy()

            // 4. Wire subscriptions so audio library events propagate to subscribers
            audioLibrary.subscribe(waveformRepository)
            audioLibrary.subscribe(playlistHierarchy)

            return MusicLibrary(audioLibrary, playlistHierarchy, waveformRepository)
        }

        @Suppress("UNCHECKED_CAST")
        private fun createAudioRepository(): Repository<Int, AudioItem> =
            when (val config = audioLibraryStorage) {
                is VolatileStorage -> VolatileRepository("AudioLibrary")
                is JsonFileStorage -> JsonFileRepository(config.file, AudioItemMapSerializer)
                is SqlStorage<*> -> SqlRepository((config as SqlStorage<AudioItem>).dataSource, config.tableDef)
            }

        @Suppress("UNCHECKED_CAST")
        private fun createPlaylistHierarchy(): DefaultPlaylistHierarchy =
            when (val config = playlistHierarchyStorage) {
                is VolatileStorage -> DefaultPlaylistHierarchy.createAndBind(VolatileRepository("PlaylistHierarchy"))
                is JsonFileStorage -> DefaultPlaylistHierarchy.createAndBind(config.file)
                is SqlStorage<*> ->
                    DefaultPlaylistHierarchy.createAndBind(
                        SqlRepository(
                            (config as SqlStorage<MutableAudioPlaylist>).dataSource,
                            config.tableDef
                        )
                    )
            }

        @Suppress("UNCHECKED_CAST")
        private fun createWaveformRepository(): Repository<Int, AudioWaveform> =
            when (val config = waveformRepositoryStorage) {
                is VolatileStorage -> VolatileRepository("AudioWaveformRepository")
                is JsonFileStorage -> JsonFileRepository(config.file, AudioWaveformMapSerializer)
                is SqlStorage<*> -> SqlRepository((config as SqlStorage<AudioWaveform>).dataSource, config.tableDef)
            }
    }

    companion object {
        /**
         * Returns a new [Builder] to configure and construct a [MusicLibrary].
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}