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
import net.transgressoft.commons.music.audio.AudioLibraryBase
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.StandardCrudEvent.Update
import net.transgressoft.lirp.persistence.LirpRepository
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import javafx.application.Platform
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleSetProperty
import javafx.collections.FXCollections
import javafx.collections.FXCollections.observableArrayList
import javafx.collections.FXCollections.observableSet
import javafx.collections.MapChangeListener
import javafx.collections.ObservableSet
import mu.KotlinLogging
import java.nio.file.Path

/**
 * JavaFX-compatible audio library with observable collections for UI binding.
 *
 * Maintains synchronized observable collections of audio items, artists, and artist catalogs
 * that automatically update when the library changes. Provides JavaFX properties for direct
 * binding to UI components, enabling reactive table views, list views, and other JavaFX
 * controls without manual synchronization.
 *
 * Registers its backing repository in [net.transgressoft.lirp.persistence.LirpContext] on construction
 * via [RegistryBase.registerRepository], enabling playlist hierarchies to resolve audio item references
 * lazily through the context. Deregisters on [close] to support repeated construction within the same JVM.
 */
@LirpRepository
internal class ObservableAudioLibrary(repository: Repository<Int, ObservableAudioItem>)
: AudioLibraryBase<ObservableAudioItem, ObservableArtistCatalog>(
    repository,
    FXArtistCatalogRegistry()
) {

    private val logger = KotlinLogging.logger {}

    private val observableAudioItemMap =
        FXCollections.observableHashMap<Int, ObservableAudioItem>().apply {
            addListener(
                MapChangeListener { change ->
                    change?.valueRemoved?.let { removed ->
                        Platform.runLater {
                            audioItemsProperty.removeIf { it.id == removed.id }
                        }
                    }
                    change?.valueAdded?.let {
                        Platform.runLater {
                            audioItemsProperty.add(it)
                        }
                    }
                }
            )
        }

    // Subscribe to its own events in order to update the observable properties
    private val internalSubscription =
        subscribe(CREATE, UPDATE, DELETE) { event ->
            synchronized(observableAudioItemMap) {
                if (event.isDelete()) {
                    event.entities.forEach {
                        observableAudioItemMap.remove(it.key)
                        if (observableAudioItemMap.values.none { audioItem -> audioItem.artist == it.value.artist }) {
                            artistsProperty.remove(it.value.artist)
                        }
                    }
                } else {
                    observableAudioItemMap.putAll(event.entities)
                    event.entities.values
                        .map(ObservableAudioItem::artistsInvolved)
                        .forEach { artistsProperty.addAll(it) }
                }
            }
        }

    private val observableArtistCatalogSet: ObservableSet<ObservableArtistCatalog> = observableSet()

    private val catalogSubscription =
        artistCatalogPublisher.subscribe(CREATE, UPDATE, DELETE) { event ->
            synchronized(observableArtistCatalogSet) {
                if (event.isDelete()) {
                    Platform.runLater {
                        observableArtistCatalogSet.removeAll(event.entities.values.toSet())
                        updateAggregateProperties()
                    }
                } else {
                    Platform.runLater {
                        observableArtistCatalogSet.addAll(event.entities.values)
                        updateAggregateProperties()
                    }
                }
            }
        }

    @get:JvmName("audioItemsProperty")
    val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> =
        SimpleListProperty(this, "observable audio items", observableArrayList())

    @get:JvmName("emptyLibraryProperty")
    val emptyLibraryProperty: ReadOnlyBooleanProperty = audioItemsProperty.emptyProperty()

    @get:JvmName("artistsProperty")
    val artistsProperty: ReadOnlySetProperty<Artist> = SimpleSetProperty(this, "artists", observableSet())

    @get:JvmName("artistCatalogsProperty")
    val artistCatalogsProperty: ReadOnlySetProperty<ObservableArtistCatalog> =
        SimpleSetProperty(this, "artist catalogs", observableSet(observableArtistCatalogSet))

    private val _albumsProperty = SimpleSetProperty<Album>(this, "albums", observableSet())

    @get:JvmName("albumsProperty")
    val albumsProperty: ReadOnlySetProperty<Album> = _albumsProperty

    private val _albumCountProperty = SimpleIntegerProperty(this, "albumCount", 0)

    @get:JvmName("albumCountProperty")
    val albumCountProperty: ReadOnlyIntegerProperty = _albumCountProperty

    init {
        RegistryBase.deregisterRepository(ObservableAudioItem::class.java)
        RegistryBase.registerRepository(ObservableAudioItem::class.java, repository)

        // Add all existing audio items to the observable collections on initialization
        forEach {
            observableAudioItemMap[it.id] = it
            Platform.runLater { artistsProperty.addAll(it.artistsInvolved) }
        }

        // Populate observable catalog set with catalogs created during initialization
        observableArtistCatalogRegistry.forEach {
            Platform.runLater { observableArtistCatalogSet.add(it) }
        }
        Platform.runLater { updateAggregateProperties() }

        // Subscribe to the player events to update the play count
        playerSubscriber.addOnNextEventAction(PLAYED) { event ->
            val audioItem = event.audioItem
            if (audioItem is FXAudioItem) {
                val audioItemClone = audioItem.clone()
                audioItem.incrementPlayCount()
                repository.emitAsync(Update(audioItem, audioItemClone))
                logger.debug { "Play count of audio item with id ${audioItem.id} increased to ${audioItem.playCount}" }
            }
        }
    }

    private fun updateAggregateProperties() {
        val allAlbums =
            observableArtistCatalogSet.flatMap { catalog ->
                catalog.albums.map { it.first().album }
            }.toSet()
        _albumsProperty.apply {
            clear()
            addAll(allAlbums)
        }
        _albumCountProperty.set(allAlbums.size)
    }

    /**
     * Cancels all event subscriptions managed by this library, including those from the base class
     * and the FX-specific internal and catalog subscriptions, then deregisters the repository from LirpContext.
     */
    override fun close() {
        super.close()
        internalSubscription.cancel()
        catalogSubscription.cancel()
        RegistryBase.deregisterRepository(ObservableAudioItem::class.java)
    }

    override fun clear() {
        synchronized(observableAudioItemMap) {
            super.clear()
            observableAudioItemMap.clear()
            observableArtistCatalogSet.clear()
            _albumsProperty.clear()
            _albumCountProperty.set(0)
        }
    }

    override fun createFromFile(audioItemPath: Path): FXAudioItem =
        FXAudioItem(audioItemPath, newId())
            .also { fxAudioItem ->
                add(fxAudioItem)
                logger.debug { "New ObservableAudioItem was created from file $audioItemPath with id ${fxAudioItem.id}" }
            }

    override fun toString() = "ObservableAudioItemJsonRepository(audioItemsCount=${size()})"
}