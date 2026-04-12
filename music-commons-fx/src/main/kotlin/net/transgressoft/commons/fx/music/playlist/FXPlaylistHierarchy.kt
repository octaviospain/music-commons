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

package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.playlist.PlaylistHierarchyBase
import net.transgressoft.commons.music.playlist.event.AudioPlaylistEventSubscriber
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import javafx.application.Platform
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleSetProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableSet
import mu.KotlinLogging

/**
 * JavaFX-compatible playlist hierarchy with observable playlist collections.
 *
 * Maintains an observable set of all playlists in the hierarchy that automatically syncs
 * with repository changes. Enables JavaFX UI components to bind directly to the playlist
 * collection and receive automatic updates when playlists are created, modified, or removed.
 * All modifications are executed on the JavaFX Application Thread for thread safety.
 *
 * Playlist deserialization is driven by `lirpSerializer(FXPlaylist(0))` passed to a
 * [net.transgressoft.lirp.persistence.json.JsonFileRepository] at construction time.
 */
internal class FXPlaylistHierarchy(
    private val repository: Repository<Int, ObservablePlaylist> = VolatileRepository("FXPlaylistHierarchy")
) : PlaylistHierarchyBase<ObservableAudioItem, ObservablePlaylist>(repository), ObservablePlaylistHierarchy {

    private val logger = KotlinLogging.logger {}

    override val playlistElementType = ObservablePlaylist::class

    private val observablePlaylistsSet: ObservableSet<ObservablePlaylist> = FXCollections.observableSet()

    override val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist> =
        SimpleSetProperty(this, "playlists", observablePlaylistsSet)

    private val playlistChangesSubscriber =
        AudioPlaylistEventSubscriber<ObservablePlaylist, ObservableAudioItem>("InternalAudioPlaylistSubscriber").apply {
            addOnNextEventAction(CREATE, UPDATE) { event ->
                synchronized(playlistsProperty) {
                    Platform.runLater { observablePlaylistsSet.addAll(event.entities.values) }
                }
            }
            addOnNextEventAction(DELETE) { event ->
                synchronized(playlistsProperty) {
                    Platform.runLater { observablePlaylistsSet.removeAll(event.entities.values.toSet()) }
                }
            }
        }

    init {
        RegistryBase.deregisterRepository(ObservablePlaylist::class.java)
        RegistryBase.registerRepository(ObservablePlaylist::class.java, repository)

        forEach { playlist ->
            Platform.runLater { observablePlaylistsSet.add(playlist) }
        }

        subscribe(playlistChangesSubscriber)
    }

    override fun createPlaylist(name: String): ObservablePlaylist = createPlaylist(name, emptyList())

    override fun createPlaylist(
        name: String,
        audioItems: List<ObservableAudioItem>
    ): ObservablePlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return FXPlaylist(newId(), name, false, audioItems.map { it.id }).also {
            logger.debug { "Created playlist $it" }
            add(it)
        }
    }

    override fun createPlaylistDirectory(name: String): ObservablePlaylist = createPlaylistDirectory(name, emptyList())

    override fun createPlaylistDirectory(
        name: String,
        audioItems: List<ObservableAudioItem>
    ): ObservablePlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return FXPlaylist(newId(), name, true, audioItems.map { it.id }).also {
            logger.debug { "Created playlist $it" }
            add(it)
        }
    }

    /**
     * Cancels the base class subscriptions and the internal playlist changes subscriber,
     * then deregisters the playlist repository from LirpContext.
     */
    override fun close() {
        super.close()
        playlistChangesSubscriber.cancelSubscription()
        RegistryBase.deregisterRepository(ObservablePlaylist::class.java)
    }

    override fun toString() = "observablePlaylistHierarchy(playlistsCount=${size()})"
}