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
import net.transgressoft.commons.fx.music.audio.ObservableArtistCatalog
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioItemMapSerializer
import net.transgressoft.commons.fx.music.audio.ObservableAudioLibrary
import net.transgressoft.commons.fx.music.playlist.FXPlaylistHierarchy
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylist
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylistHierarchy
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylistMapSerializer
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.music.waveform.audioWaveformRepository
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.persistence.PersistentRepository
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.sql.SqlRepository
import net.transgressoft.lirp.persistence.sql.SqlTableDef
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlySetProperty
import java.io.File
import java.nio.file.Path
import java.util.Optional
import javax.sql.DataSource

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
 *     .audioLibraryJsonFile(audioFile)
 *     .playlistHierarchyJsonFile(playlistFile)
 *     .build()
 * ```
 */
class FXMusicLibrary private constructor(
    private val _audioLibrary: FXAudioLibrary,
    private val _playlistHierarchy: FXPlaylistHierarchy,
    private val _waveformRepository: AudioWaveformRepository<AudioWaveform, ObservableAudioItem>
) : AutoCloseable {

    /** Returns the underlying observable audio library for advanced operations. */
    fun audioLibrary(): ObservableAudioLibrary = _audioLibrary

    /** Returns the underlying observable playlist hierarchy for advanced operations. */
    fun playlistHierarchy(): ObservablePlaylistHierarchy = _playlistHierarchy

    /** Returns the underlying waveform repository for advanced operations. */
    fun waveformRepository(): AudioWaveformRepository<AudioWaveform, ObservableAudioItem> = _waveformRepository

    /** Observable list of all audio items in the library, suitable for direct JavaFX binding. */
    val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> get() = _audioLibrary.audioItemsProperty

    /** Boolean property that is true when the library contains no audio items. */
    val emptyLibraryProperty: ReadOnlyBooleanProperty get() = _audioLibrary.emptyLibraryProperty

    /** Observable set of all distinct artists in the library. */
    val artistsProperty: ReadOnlySetProperty<Artist> get() = _audioLibrary.artistsProperty

    /** Observable set of all artist catalogs, each grouping albums and items by artist. */
    val artistCatalogsProperty: ReadOnlySetProperty<ObservableArtistCatalog> get() = _audioLibrary.artistCatalogsProperty

    /** Observable set of all albums across all artists in the library. */
    val albumsProperty: ReadOnlySetProperty<Album> get() = _audioLibrary.albumsProperty

    /** Observable count of distinct albums in the library. */
    val albumCountProperty: ReadOnlyIntegerProperty get() = _audioLibrary.albumCountProperty

    /** Observable set of all playlists in the hierarchy. */
    val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist> get() = _playlistHierarchy.playlistsProperty

    /**
     * Subscriber that receives [AudioItemPlayerEvent] notifications.
     *
     * Subscribe an [net.transgressoft.commons.music.player.AudioItemPlayer] to this subscriber
     * so that play events increment the play count on the corresponding audio item.
     */
    val playerSubscriber get() = _audioLibrary.playerSubscriber

    /**
     * Publisher that emits artist catalog [CrudEvent] notifications.
     *
     * Subscribe to this publisher to receive updates when artist catalogs are created,
     * updated, or removed as audio items are added or deleted.
     */
    val artistCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Artist, ObservableArtistCatalog>>
        get() = _audioLibrary.artistCatalogPublisher

    /**
     * Creates an [ObservableAudioItem] from the audio file at [path] and adds it to the library.
     *
     * @param path the path to the audio file
     * @return the created [ObservableAudioItem]
     */
    fun audioItemFromFile(path: Path): ObservableAudioItem = _audioLibrary.createFromFile(path)

    /**
     * Creates a new playlist with the given [name].
     *
     * @param name the unique playlist name
     * @return the created [ObservablePlaylist]
     * @throws IllegalArgumentException if a playlist with [name] already exists
     */
    fun createPlaylist(name: String): ObservablePlaylist = _playlistHierarchy.createPlaylist(name)

    /**
     * Creates a new playlist directory with the given [name].
     *
     * @param name the unique directory name
     * @return the created [ObservablePlaylist] directory
     * @throws IllegalArgumentException if a playlist with [name] already exists
     */
    fun createPlaylistDirectory(name: String): ObservablePlaylist = _playlistHierarchy.createPlaylistDirectory(name)

    /**
     * Moves the playlist named [playlistNameToMove] into the playlist named [destinationPlaylistName].
     *
     * @param playlistNameToMove the name of the playlist to move
     * @param destinationPlaylistName the name of the destination playlist directory
     * @throws IllegalArgumentException if either playlist does not exist
     */
    fun movePlaylist(playlistNameToMove: String, destinationPlaylistName: String) =
        _playlistHierarchy.movePlaylist(playlistNameToMove, destinationPlaylistName)

    /**
     * Finds the playlist with the given [name].
     *
     * @param name the playlist name to search for
     * @return an [Optional] containing the playlist, or empty if not found
     */
    fun findPlaylistByName(name: String): Optional<out ObservablePlaylist> = _playlistHierarchy.findByName(name)

    override fun close() {
        _playlistHierarchy.close()
        _waveformRepository.close()
        _audioLibrary.close()
    }

    /**
     * Builder for [FXMusicLibrary].
     *
     * All storage configurations default to volatile (in-memory) unless overridden with a JSON file
     * or SQL data source:
     *
     * ```kotlin
     * val library = FXMusicLibrary.builder()
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

        /** Configures the audio library to use JSON file persistence at [file]. */
        fun audioLibraryJsonFile(file: File): Builder = apply { audioLibraryStorage = JsonFileStorage(file) }

        /** Configures the playlist hierarchy to use JSON file persistence at [file]. */
        fun playlistHierarchyJsonFile(file: File): Builder = apply { playlistHierarchyStorage = JsonFileStorage(file) }

        /** Configures the waveform repository to use JSON file persistence at [file]. */
        fun waveformRepositoryJsonFile(file: File): Builder = apply { waveformRepositoryStorage = JsonFileStorage(file) }

        /**
         * Configures the audio library to persist to a SQL database using [dataSource] and [tableDef].
         *
         * @param dataSource the JDBC data source for database connections
         * @param tableDef the KSP-generated table definition describing [ObservableAudioItem] column mapping
         */
        fun audioLibrarySql(dataSource: DataSource, tableDef: SqlTableDef<ObservableAudioItem>): Builder =
            apply { audioLibraryStorage = SqlStorage(dataSource, tableDef) }

        /**
         * Configures the playlist hierarchy to persist to a SQL database using [dataSource] and [tableDef].
         *
         * @param dataSource the JDBC data source for database connections
         * @param tableDef the KSP-generated table definition describing [ObservablePlaylist] column mapping
         */
        fun playlistHierarchySql(dataSource: DataSource, tableDef: SqlTableDef<ObservablePlaylist>): Builder =
            apply { playlistHierarchyStorage = SqlStorage(dataSource, tableDef) }

        /**
         * Configures the waveform repository to persist to a SQL database using [dataSource] and [tableDef].
         *
         * @param dataSource the JDBC data source for database connections
         * @param tableDef the KSP-generated table definition describing [AudioWaveform] column mapping
         */
        fun waveformRepositorySql(dataSource: DataSource, tableDef: SqlTableDef<AudioWaveform>): Builder =
            apply { waveformRepositoryStorage = SqlStorage(dataSource, tableDef) }

        /** Builds the [FXMusicLibrary], wiring all event subscriptions between components. */
        fun build(): FXMusicLibrary {
            val audioRepo = createAudioRepository()
            val audioLibrary = FXAudioLibrary(audioRepo)
            var playlistRepoRegistered = false

            var waveformRepository: AudioWaveformRepository<AudioWaveform, ObservableAudioItem>? = null
            try {
                val waveformRepo = createWaveformRepository()
                waveformRepository = audioWaveformRepository(waveformRepo)

                val playlistRepo = createPlaylistRepository()
                if (playlistRepo is PersistentRepository<*, *>) {
                    RegistryBase.registerRepository(ObservablePlaylist::class.java, playlistRepo)
                    playlistRepoRegistered = true
                    playlistRepo.load()
                }
                val playlistHierarchy = FXPlaylistHierarchy(playlistRepo)

                try {
                    audioLibrary.subscribe(waveformRepository)
                    audioLibrary.subscribe(playlistHierarchy)
                } catch (subscribeEx: Exception) {
                    playlistHierarchy.close()
                    throw subscribeEx
                }

                return FXMusicLibrary(audioLibrary, playlistHierarchy, waveformRepository)
            } catch (ex: Exception) {
                if (playlistRepoRegistered) {
                    RegistryBase.deregisterRepository(ObservablePlaylist::class.java)
                }
                waveformRepository?.close()
                audioLibrary.close()
                throw ex
            }
        }

        // Safe cast: generic type erased at runtime but guaranteed by the builder/serializer contract
        @Suppress("UNCHECKED_CAST")
        private fun createAudioRepository(): Repository<Int, ObservableAudioItem> =
            when (val config = audioLibraryStorage) {
                is VolatileStorage -> VolatileRepository("FXAudioLibrary")
                is JsonFileStorage -> JsonFileRepository(config.file, ObservableAudioItemMapSerializer)
                is SqlStorage<*> -> SqlRepository((config as SqlStorage<ObservableAudioItem>).dataSource, config.tableDef)
            }

        @Suppress("UNCHECKED_CAST")
        private fun createPlaylistRepository(): Repository<Int, ObservablePlaylist> =
            when (val config = playlistHierarchyStorage) {
                is VolatileStorage -> VolatileRepository("FXPlaylistHierarchy")
                is JsonFileStorage -> JsonFileRepository(config.file, ObservablePlaylistMapSerializer, loadOnInit = false)
                is SqlStorage<*> ->
                    SqlRepository(
                        (config as SqlStorage<ObservablePlaylist>).dataSource,
                        config.tableDef,
                        loadOnInit = false
                    )
            }

        @Suppress("UNCHECKED_CAST")
        private fun createWaveformRepository(): Repository<Int, AudioWaveform> =
            when (val config = waveformRepositoryStorage) {
                is VolatileStorage -> VolatileRepository("FXWaveformRepository")
                is JsonFileStorage -> JsonFileRepository(config.file, AudioWaveformMapSerializer)
                is SqlStorage<*> -> SqlRepository((config as SqlStorage<AudioWaveform>).dataSource, config.tableDef)
            }
    }

    companion object {

        /**
         * Returns a new [Builder] for constructing an [FXMusicLibrary].
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    private sealed interface StorageConfig

    private object VolatileStorage : StorageConfig

    private class JsonFileStorage(val file: File) : StorageConfig

    private class SqlStorage<E>(val dataSource: DataSource, val tableDef: SqlTableDef<E>) : StorageConfig
}