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
import net.transgressoft.lirp.persistence.fx.FxAggregateList
import net.transgressoft.lirp.persistence.fx.fxAggregateList
import javafx.application.Platform
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleSetProperty
import javafx.collections.FXCollections.observableSet
import javafx.collections.ObservableSet
import mu.KotlinLogging
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArraySet

/**
 * JavaFX-compatible audio library with observable collections for UI binding.
 *
 * Maintains an [FxAggregateList] as the primary observable items collection. The [audioItemsProperty]
 * wraps this delegate directly. The [artistsProperty] tracks all artists from
 * each item's [ObservableAudioItem.artistsInvolved] set and is updated inline in the CRUD subscription.
 * The [artistCatalogsProperty] is backed by an [ObservableSet] updated directly from the catalog
 * subscription (synchronously, without [Platform.runLater]), and album-level derived properties
 * ([albumsProperty], [albumCountProperty]) update via [Platform.runLater] from the same subscription.
 *
 * Registers its backing repository in [net.transgressoft.lirp.persistence.LirpContext] on construction
 * via [RegistryBase.registerRepository], enabling playlist hierarchies to resolve audio item references
 * lazily through the context. Deregisters on [close] to support repeated construction within the same JVM.
 */
@LirpRepository
internal class FXAudioLibrary(repository: Repository<Int, ObservableAudioItem>)
: AudioLibraryBase<ObservableAudioItem, ObservableArtistCatalog>(
    repository,
    FXArtistCatalogRegistry()
),
    ObservableAudioLibrary {

    private val logger = KotlinLogging.logger {}

    // Primary observable items collection -- populated from CRUD events, auto-dispatches to FX thread
    private val audioItems: FxAggregateList<Int, ObservableAudioItem> by fxAggregateList()

    @get:JvmName("audioItemsProperty")
    override val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> =
        SimpleListProperty(this, "observable audio items", audioItems)

    @get:JvmName("emptyLibraryProperty")
    override val emptyLibraryProperty: ReadOnlyBooleanProperty = audioItemsProperty.emptyProperty()

    @get:JvmName("artistsProperty")
    override val artistsProperty: ReadOnlySetProperty<Artist> = SimpleSetProperty(this, "artists", observableSet())

    // Thread-safe backing for artist catalogs -- updated directly from catalog subscription
    private val artistCatalogBacking: CopyOnWriteArraySet<ObservableArtistCatalog> = CopyOnWriteArraySet()

    private val observableArtistCatalogSet: ObservableSet<ObservableArtistCatalog> = observableSet()

    @get:JvmName("artistCatalogsProperty")
    override val artistCatalogsProperty: ReadOnlySetProperty<ObservableArtistCatalog> =
        SimpleSetProperty(this, "artist catalogs", observableArtistCatalogSet)

    private val _albumsProperty = SimpleSetProperty<Album>(this, "albums", observableSet())

    @get:JvmName("albumsProperty")
    override val albumsProperty: ReadOnlySetProperty<Album> = _albumsProperty

    private val _albumCountProperty = SimpleIntegerProperty(this, "albumCount", 0)

    @get:JvmName("albumCountProperty")
    override val albumCountProperty: ReadOnlyIntegerProperty = _albumCountProperty

    // Subscribe to repository events to populate audioItemsDelegate and artistsProperty.
    // artistsProperty tracks all artists from artistsInvolved (primary + featured artists).
    private val internalSubscription =
        subscribe(CREATE, UPDATE, DELETE) { event ->
            if (event.isDelete()) {
                val idsToRemove = event.entities.keys
                audioItems.removeAll(
                    audioItems.filter { it.id in idsToRemove }
                )
                // Remove artists no longer represented in the remaining items
                event.entities.values.forEach { removed ->
                    removed.artistsInvolved.forEach { artist ->
                        if (audioItems.none { item -> artist in item.artistsInvolved }) {
                            artistsProperty.remove(artist)
                        }
                    }
                }
            } else {
                event.entities.values.forEach { item ->
                    if (audioItems.none { it.id == item.id }) {
                        audioItems.add(item)
                    }
                    artistsProperty.addAll(item.artistsInvolved)
                }
            }
        }

    private val catalogSubscription =
        artistCatalogPublisher.subscribe(CREATE, UPDATE, DELETE) { event ->
            if (event.isDelete()) {
                artistCatalogBacking.removeAll(event.entities.values.toSet())
            } else {
                artistCatalogBacking.addAll(event.entities.values)
            }
            // Synchronously update artistCatalogsProperty from backing set
            observableArtistCatalogSet.apply {
                clear()
                addAll(artistCatalogBacking)
            }
            // Auto-derive albums and album count from current catalog state
            val allAlbums =
                artistCatalogBacking.flatMap { catalog ->
                    catalog.albums.map { it.first().album }
                }.toSet()
            Platform.runLater {
                _albumsProperty.apply {
                    clear()
                    addAll(allAlbums)
                }
                _albumCountProperty.set(allAlbums.size)
            }
        }

    init {
        RegistryBase.deregisterRepository(ObservableAudioItem::class.java)
        RegistryBase.registerRepository(ObservableAudioItem::class.java, repository)

        // Populate fxAggregateList and artistsProperty from existing items
        forEach { item ->
            audioItems.add(item)
            artistsProperty.addAll(item.artistsInvolved)
        }

        // Populate catalog backing from registries created during AudioLibraryBase init
        observableArtistCatalogRegistry.forEach { artistCatalogBacking.add(it) }
        observableArtistCatalogSet.addAll(artistCatalogBacking)
        // Auto-derive initial albums/albumCount
        val allAlbums =
            artistCatalogBacking.flatMap { catalog ->
                catalog.albums.map { it.first().album }
            }.toSet()
        Platform.runLater {
            _albumsProperty.addAll(allAlbums)
            _albumCountProperty.set(allAlbums.size)
        }

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
        super.clear()
        audioItems.clear()
        artistCatalogBacking.clear()
        observableArtistCatalogSet.clear()
        _albumsProperty.clear()
        _albumCountProperty.set(0)
    }

    override fun createFromFile(audioItemPath: Path): FXAudioItem =
        FXAudioItem(audioItemPath, newId())
            .also { fxAudioItem ->
                add(fxAudioItem)
                logger.debug { "New ObservableAudioItem was created from file $audioItemPath with id ${fxAudioItem.id}" }
            }

    override fun toString() = "ObservableAudioItemJsonRepository(audioItemsCount=${size()})"
}