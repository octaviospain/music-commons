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
import net.transgressoft.commons.music.audio.AudioMetadataIO
import net.transgressoft.commons.music.audio.JAudioTaggerMetadataIO
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
import net.transgressoft.commons.util.InvalidAudioFilePathException
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.ReactiveScope
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
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * JavaFX-compatible audio library with observable collections for UI binding.
 *
 * Maintains an [FxAggregateList] as the primary observable items collection. Repository and catalog
 * events can arrive in large background bursts during directory imports, so JavaFX-facing collections
 * are refreshed through coalesced [Platform.runLater] drains instead of one JavaFX task per entity.
 *
 * Registers its backing repository in [net.transgressoft.lirp.persistence.LirpContext] on construction
 * via [RegistryBase.registerRepository], enabling playlist hierarchies to resolve audio item references
 * lazily through the context. Deregisters on [close] to support repeated construction within the same JVM.
 *
 * The [Platform.runLater] dispatches intentionally omit error handling because they perform only simple
 * collection and property mutations; any exception indicates a programming error that should surface
 * through the default uncaught exception handler.
 */
@LirpRepository
internal class FXAudioLibrary
    @JvmOverloads
    constructor(
        repository: Repository<Int, ObservableAudioItem>,
        metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()
    ) : AudioLibraryBase<ObservableAudioItem, ObservableArtistCatalog>(
            repository,
            FXArtistCatalogRegistry(repository),
            metadataIO
        ),
        ObservableAudioLibrary {

        private val logger = KotlinLogging.logger {}

        private companion object {
            const val FX_REFRESH_DEBOUNCE_MILLIS = 16L
        }

        // Primary observable items collection -- populated from CRUD events, auto-dispatches to FX thread
        private val audioItems: FxAggregateList<Int, ObservableAudioItem> by fxAggregateList()
        private val audioItemIds = ConcurrentHashMap.newKeySet<Int>()
        private val pendingCreatedAudioItems = ConcurrentLinkedQueue<ObservableAudioItem>()
        private val pendingDeletedAudioItemIds = ConcurrentLinkedQueue<Int>()
        private val audioItemsRefreshQueued = AtomicBoolean(false)
        private val recomputeArtistsRequested = AtomicBoolean(false)

        @get:JvmName("audioItemsProperty")
        override val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> =
            SimpleListProperty(this, "observable audio items", audioItems)

        @get:JvmName("emptyLibraryProperty")
        override val emptyLibraryProperty: ReadOnlyBooleanProperty = audioItemsProperty.emptyProperty()

        @get:JvmName("artistsProperty")
        override val artistsProperty: ReadOnlySetProperty<Artist> = SimpleSetProperty(this, "artists", observableSet())

        // Thread-safe backing for artist catalogs -- updated directly from catalog subscription
        private val artistCatalogBacking: CopyOnWriteArraySet<ObservableArtistCatalog> = CopyOnWriteArraySet()
        private val catalogRefreshQueued = AtomicBoolean(false)
        private val catalogRefreshRequested = AtomicBoolean(false)

        private val observableArtistCatalogSet: ObservableSet<ObservableArtistCatalog> = observableSet()

        @get:JvmName("artistCatalogsProperty")
        override val artistCatalogsProperty: ReadOnlySetProperty<ObservableArtistCatalog> =
            SimpleSetProperty(this, "artist catalogs", observableArtistCatalogSet)

        @get:JvmName("albumsProperty")
        override val albumsProperty: ReadOnlySetProperty<Album>
            field = SimpleSetProperty<Album>(this, "albums", observableSet())

        @get:JvmName("albumCountProperty")
        override val albumCountProperty: ReadOnlyIntegerProperty
            field = SimpleIntegerProperty(this, "albumCount", 0)

        // Subscribe to repository events to populate audioItemsDelegate and artistsProperty.
        // artistsProperty tracks all artists from artistsInvolved (primary + featured artists).
        private val internalSubscription =
            subscribe(CREATE, UPDATE, DELETE) { event ->
                if (event.isDelete()) {
                    event.entities.keys.forEach { id ->
                        audioItemIds.remove(id)
                        pendingDeletedAudioItemIds.add(id)
                    }
                    recomputeArtistsRequested.set(true)
                    queueAudioItemsRefresh()
                } else if (event.isCreate()) {
                    event.entities.values.forEach { item ->
                        if (audioItemIds.add(item.id)) {
                            pendingCreatedAudioItems.add(item)
                        }
                    }
                    queueAudioItemsRefresh()
                } else {
                    recomputeArtistsRequested.set(true)
                    queueAudioItemsRefresh()
                }
            }

        private val catalogSubscription =
            artistCatalogPublisher.subscribe(CREATE, UPDATE, DELETE) { event ->
                if (event.isDelete()) {
                    artistCatalogBacking.removeAll(event.entities.values.toSet())
                } else if (event.isUpdate()) {
                    // Replace old catalog with new: remove the stale version before adding the rebuilt one.
                    // Each bucket recompute produces a fresh FXArtistCatalog instance, so the old
                    // and new instances are not equal and both would accumulate without this removal.
                    artistCatalogBacking.removeAll(event.oldEntities.values.toSet())
                    artistCatalogBacking.addAll(event.entities.values)
                } else {
                    artistCatalogBacking.addAll(event.entities.values)
                }
                queueCatalogRefresh()
            }

        init {
            RegistryBase.deregisterRepository(ObservableAudioItem::class.java)
            RegistryBase.registerRepository(ObservableAudioItem::class.java, repository)

            // Populate fxAggregateList and artistsProperty from existing items
            val initialAudioItems = toList()
            // Wire the library back-ref on items rehydrated from JSON so coverImageBytes can lazy-load.
            initialAudioItems.forEach { item ->
                if (item is FXAudioItem) item.metadataIO = metadataIO
            }
            audioItemIds.addAll(initialAudioItems.map { it.id })
            if (initialAudioItems.isNotEmpty()) {
                Platform.runLater {
                    audioItems.addAll(initialAudioItems)
                    artistsProperty.addAll(initialAudioItems.flatMap { it.artistsInvolved })
                }
            }

            // Catalog backing is populated reactively via catalogSubscription as the projection
            // fires CREATE events during its lazy initialization on first access.
            queueCatalogRefresh()

            // Subscribe to the player events to update the play count
            playerSubscriber.addOnNextEventAction(PLAYED) { event ->
                val audioItem = event.audioItem
                if (audioItem is FXAudioItem) {
                    audioItem.incrementPlayCount()
                    logger.debug { "Play count of audio item with id ${audioItem.id} increased to ${audioItem.playCount}" }
                }
            }
        }

        private fun queueAudioItemsRefresh() {
            if (!audioItemsRefreshQueued.compareAndSet(false, true)) {
                return
            }
            ReactiveScope.flowScope.launch {
                delay(FX_REFRESH_DEBOUNCE_MILLIS.milliseconds)
                Platform.runLater {
                    try {
                        drainAudioItemChanges()
                    } finally {
                        audioItemsRefreshQueued.set(false)
                        if (hasPendingAudioItemRefresh()) {
                            queueAudioItemsRefresh()
                        }
                    }
                }
            }
        }

        private fun drainAudioItemChanges() {
            val deletedIds = generateSequence { pendingDeletedAudioItemIds.poll() }.toSet()
            if (deletedIds.isNotEmpty()) {
                audioItems.removeAll(audioItems.filter { it.id in deletedIds })
            }

            val createdAudioItems =
                generateSequence { pendingCreatedAudioItems.poll() }
                    .filter { it.id in audioItemIds }
                    .toList()
            if (createdAudioItems.isNotEmpty()) {
                audioItems.addAll(createdAudioItems)
            }

            if (recomputeArtistsRequested.getAndSet(false)) {
                val allArtists = audioItems.flatMap { it.artistsInvolved }.toSet()
                artistsProperty.retainAll(allArtists)
                artistsProperty.addAll(allArtists)
            } else if (createdAudioItems.isNotEmpty()) {
                artistsProperty.addAll(createdAudioItems.flatMap { it.artistsInvolved })
            }
        }

        private fun hasPendingAudioItemRefresh(): Boolean =
            pendingCreatedAudioItems.isNotEmpty() || pendingDeletedAudioItemIds.isNotEmpty() || recomputeArtistsRequested.get()

        private fun queueCatalogRefresh() {
            catalogRefreshRequested.set(true)
            if (!catalogRefreshQueued.compareAndSet(false, true)) {
                return
            }
            ReactiveScope.flowScope.launch {
                delay(FX_REFRESH_DEBOUNCE_MILLIS.milliseconds)
                Platform.runLater {
                    try {
                        catalogRefreshRequested.set(false)
                        refreshCatalogProperties()
                    } finally {
                        catalogRefreshQueued.set(false)
                        if (catalogRefreshRequested.get()) {
                            queueCatalogRefresh()
                        }
                    }
                }
            }
        }

        private fun refreshCatalogProperties() {
            observableArtistCatalogSet.apply {
                clear()
                addAll(artistCatalogBacking)
            }
            val allAlbums =
                artistCatalogBacking.flatMap { catalog ->
                    catalog.albums.map { it.first().album }
                }.toSet()
            albumsProperty.apply {
                clear()
                addAll(allAlbums)
            }
            albumCountProperty.set(allAlbums.size)
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
            audioItemIds.clear()
            pendingCreatedAudioItems.clear()
            pendingDeletedAudioItemIds.clear()
            Platform.runLater {
                audioItems.clear()
                artistsProperty.clear()
            }
            artistCatalogBacking.clear()
            queueCatalogRefresh()
        }

        override fun createFromFile(audioItemPath: Path): FXAudioItem {
            if (!Files.exists(audioItemPath)) {
                throw InvalidAudioFilePathException("File '${audioItemPath.toAbsolutePath()}' does not exist")
            }
            if (!Files.isRegularFile(audioItemPath)) {
                throw InvalidAudioFilePathException("Path '${audioItemPath.toAbsolutePath()}' is not a regular file")
            }
            if (!Files.isReadable(audioItemPath)) {
                throw InvalidAudioFilePathException("File '${audioItemPath.toAbsolutePath()}' is not readable")
            }
            val metadata = metadataIO.readMetadata(audioItemPath)
            return FXAudioItem(audioItemPath, newId(), metadata).also { fxAudioItem ->
                add(fxAudioItem)
                logger.debug { "New ObservableAudioItem was created from file $audioItemPath with id ${fxAudioItem.id}" }
            }
        }

        override fun add(entity: ObservableAudioItem): Boolean {
            if (entity is FXAudioItem) entity.metadataIO = metadataIO
            return super.add(entity)
        }

        override fun toString() = "FXAudioLibrary(audioItemsCount=${size()})"
    }