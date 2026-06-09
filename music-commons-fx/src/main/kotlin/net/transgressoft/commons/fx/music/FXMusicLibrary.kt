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
import net.transgressoft.commons.fx.music.audio.ObservableAudioLibrary
import net.transgressoft.commons.fx.music.playlist.FXPlaylistHierarchy
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylist
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylistHierarchy
import net.transgressoft.commons.media.waveform.audioWaveformRepository
import net.transgressoft.commons.music.MusicLibrary
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioMetadataIO
import net.transgressoft.commons.music.audio.JAudioTaggerMetadataIO
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.util.WindowsPathValidator
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.persistence.PersistentRepository
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlySetProperty
import java.nio.file.Path
import java.util.Optional

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
 */
class FXMusicLibrary private constructor(
    private val _audioLibrary: FXAudioLibrary,
    private val _playlistHierarchy: FXPlaylistHierarchy,
    private val _waveformRepository: AudioWaveformRepository<AudioWaveform, ObservableAudioItem>
) : MusicLibrary<ObservableAudioItem, ObservablePlaylist> {

    /** Returns the underlying observable audio library for advanced operations. */
    override fun audioLibrary(): ObservableAudioLibrary = _audioLibrary

    /** Returns the underlying observable playlist hierarchy for advanced operations. */
    override fun playlistHierarchy(): ObservablePlaylistHierarchy = _playlistHierarchy

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
    override fun audioItemFromFile(path: Path): ObservableAudioItem {
        WindowsPathValidator.validatePath(path)
        return _audioLibrary.createFromFile(path)
    }

    override fun createPlaylist(name: String): ObservablePlaylist = _playlistHierarchy.createPlaylist(name)

    /**
     * Creates a new playlist with [name] pre-populated with [audioItems].
     *
     * @param name the unique playlist name
     * @param audioItems the audio items to include in the playlist
     * @return the created [ObservablePlaylist]
     * @throws IllegalArgumentException if a playlist with [name] already exists
     */
    fun createPlaylist(name: String, audioItems: List<ObservableAudioItem>): ObservablePlaylist =
        _playlistHierarchy.createPlaylist(name, audioItems)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createPlaylistWithIds")
    override fun createPlaylist(name: String, audioItemIds: List<Int>): ObservablePlaylist =
        _playlistHierarchy.createPlaylist(name, audioItemIds)

    override fun createPlaylistDirectory(name: String): ObservablePlaylist = _playlistHierarchy.createPlaylistDirectory(name)

    override fun movePlaylist(playlistNameToMove: String, destinationPlaylistName: String) =
        _playlistHierarchy.movePlaylist(playlistNameToMove, destinationPlaylistName)

    override fun findPlaylistByName(name: String): Optional<out ObservablePlaylist> = _playlistHierarchy.findByName(name)

    override fun close() {
        _playlistHierarchy.close()
        _waveformRepository.close()
        _audioLibrary.close()
    }

    /**
     * Builder for [FXMusicLibrary] that accepts a [Repository] implementation directly for each component.
     *
     * Each component defaults to a [VolatileRepository] (in-memory). Inject a different repository
     * implementation — `JsonFileRepository`, `SqlRepository`, or a custom one — to configure
     * persistence. The `lirp-sql` artifact and a JDBC driver are optional consumer dependencies:
     * add them to your build only if you intend to back any component with a SQL repository.
     *
     * For SQL persistence of audio items, the KSP-generated `FXAudioItem_LirpTableDef` is a ready
     * `SqlTableDef<ObservableAudioItem>`: its `fromRow` rebuilds the entity and reconstructs the
     * flyweight `Artist`/`Label` embeddables through their `@PersistenceCreator` `of()` factories.
     * Pass it straight to a `SqlRepository`/`SqliteRepository`.
     *
     * ```kotlin
     * // consumer build.gradle — a JDBC driver is a consumer-side dependency
     * // implementation("org.xerial:sqlite-jdbc:<version>")
     *
     * val library = FXMusicLibrary.builder()
     *     .audioRepository(SqliteRepository.fileBacked(audioItemsPath, FXAudioItem_LirpTableDef))
     *     .build()
     * ```
     */
    class Builder {

        private var audioRepository: Repository<Int, ObservableAudioItem> = VolatileRepository("FXAudioLibrary")
        private var playlistRepository: Repository<Int, ObservablePlaylist> = VolatileRepository("FXPlaylistHierarchy")
        private var waveformRepository: Repository<Int, AudioWaveform> = VolatileRepository("FXWaveformRepository")
        private var metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()

        /** Injects the [repository] backing the observable audio library. */
        fun audioRepository(repository: Repository<Int, ObservableAudioItem>): Builder =
            apply { audioRepository = repository }

        /** Injects the [repository] backing the observable playlist hierarchy. */
        fun playlistRepository(repository: Repository<Int, ObservablePlaylist>): Builder =
            apply { playlistRepository = repository }

        /** Injects the [repository] backing the waveform repository. */
        fun waveformRepository(repository: Repository<Int, AudioWaveform>): Builder =
            apply { waveformRepository = repository }

        /**
         * Injects the [AudioMetadataIO] used by the audio library for metadata I/O.
         *
         * Defaults to the JAudioTagger-backed implementation. Production callers rarely override
         * this — the seam exists primarily for tests that route reads/writes through a fake.
         */
        fun metadataIO(utils: AudioMetadataIO): Builder = apply { metadataIO = utils }

        /** Builds the [FXMusicLibrary], wiring all event subscriptions between components. */
        fun build(): FXMusicLibrary {
            val audioLibrary = FXAudioLibrary(audioRepository, metadataIO)
            var playlistRepoRegistered = false

            var waveformRepo: AudioWaveformRepository<AudioWaveform, ObservableAudioItem>? = null
            try {
                val audioItemSubscriber = AudioItemEventSubscriber<ObservableAudioItem>("AudioWaveformRepositorySubscriber")
                waveformRepo =
                    audioWaveformRepository<ObservableAudioItem>(
                        waveformRepository,
                        audioItemSubscriber
                    ) { audioItemSubscriber.cancelSubscription() }

                if (playlistRepository is PersistentRepository<*, *>) {
                    RegistryBase.registerRepository(ObservablePlaylist::class.java, playlistRepository)
                    playlistRepoRegistered = true
                    (playlistRepository as PersistentRepository<*, *>).load()
                }
                val playlistHierarchy = FXPlaylistHierarchy(playlistRepository)

                try {
                    audioLibrary.subscribe(waveformRepo)
                    audioLibrary.subscribe(playlistHierarchy)
                } catch (subscribeEx: Exception) {
                    playlistHierarchy.close()
                    throw subscribeEx
                }

                return FXMusicLibrary(audioLibrary, playlistHierarchy, waveformRepo)
            } catch (ex: Exception) {
                if (playlistRepoRegistered) {
                    RegistryBase.deregisterRepository(ObservablePlaylist::class.java)
                }
                waveformRepo?.close()
                audioLibrary.close()
                throw ex
            }
        }
    }

    companion object {

        /**
         * Returns a new [Builder] for constructing an [FXMusicLibrary].
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}