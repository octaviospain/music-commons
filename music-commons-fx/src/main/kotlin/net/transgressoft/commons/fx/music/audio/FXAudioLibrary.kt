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

import net.transgressoft.commons.fx.music.conditionalDeregister
import net.transgressoft.commons.fx.music.guardedRegister
import net.transgressoft.commons.music.audio.AudioLibraryBase
import net.transgressoft.commons.music.audio.AudioMetadataIO
import net.transgressoft.commons.music.audio.JAudioTaggerMetadataIO
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
import net.transgressoft.commons.util.InvalidAudioFilePathException
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.fx.FxAggregateList
import net.transgressoft.lirp.persistence.fx.fxAggregateList
import javafx.application.Platform
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleSetProperty
import javafx.collections.FXCollections.observableArrayList
import javafx.collections.FXCollections.observableSet
import javafx.collections.ObservableList
import javafx.collections.ObservableSet
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * JavaFX-compatible audio library with observable collections for UI binding.
 *
 * Maintains an [FxAggregateList] as the primary observable items collection. Repository and catalog
 * events can arrive in large background bursts during directory imports, so JavaFX-facing collections
 * are refreshed through coalesced [Platform.runLater] drains instead of one JavaFX task per entity.
 *
 * Registers its backing repository in [net.transgressoft.lirp.persistence.LirpContext] on construction,
 * enabling playlist hierarchies to resolve audio item references lazily through the context. Only one live
 * instance is permitted per JVM; constructing a second while one is live throws [IllegalStateException].
 * [close] conditionally deregisters only when this instance still owns the slot, so closing one library
 * never disturbs a concurrently constructed replacement.
 *
 * The [Platform.runLater] dispatches intentionally omit error handling because they perform only simple
 * collection and property mutations; any exception indicates a programming error that should surface
 * through the default uncaught exception handler.
 */
internal class FXAudioLibrary
    @JvmOverloads
    constructor(
        repository: Repository<Int, ObservableAudioItem>,
        metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()
    ) : AudioLibraryBase<ObservableAudioItem, ObservableArtistCatalog, ObservableAlbum, ObservableGenreIndex>(
            repository,
            FXArtistCatalogRegistry(repository),
            FXAlbumRegistry(repository),
            FXGenreIndexRegistry(repository),
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

        @get:JvmName("audioItemsProperty")
        override val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> =
            SimpleListProperty(this, "observable audio items", audioItems)

        @get:JvmName("emptyLibraryProperty")
        override val emptyLibraryProperty: ReadOnlyBooleanProperty = audioItemsProperty.emptyProperty()

        // Thread-safe backing for artist catalogs -- updated directly from catalog subscription
        private val artistCatalogBacking: CopyOnWriteArraySet<ObservableArtistCatalog> = CopyOnWriteArraySet()
        private val catalogRefreshQueued = AtomicBoolean(false)
        private val catalogRefreshRequested = AtomicBoolean(false)

        private val observableArtistCatalogSet: ObservableSet<ObservableArtistCatalog> = observableSet()

        @get:JvmName("artistCatalogsProperty")
        override val artistCatalogsProperty: ReadOnlySetProperty<ObservableArtistCatalog> =
            SimpleSetProperty(this, "artist catalogs", observableArtistCatalogSet)

        // Ordered observable list for album buckets -- populated from the ordered projection on each refresh
        private val observableAlbumList: ObservableList<ObservableAlbum> = observableArrayList()

        @get:JvmName("albumsProperty")
        override val albumsProperty: ReadOnlyListProperty<ObservableAlbum> =
            SimpleListProperty(this, "albums", observableAlbumList)

        // Ordered observable list for genre indexes -- populated from the ordered projection on each refresh
        private val observableGenreIndexList: ObservableList<ObservableGenreIndex> = observableArrayList()

        @get:JvmName("genreIndexesProperty")
        override val genreIndexesProperty: ReadOnlyListProperty<ObservableGenreIndex> =
            SimpleListProperty(this, "genre indexes", observableGenreIndexList)

        // Subscribe to repository events to populate audioItemsDelegate.
        private val internalSubscription =
            subscribe(CREATE, UPDATE, DELETE) { event ->
                if (event.isDelete()) {
                    event.entities.keys.forEach { id ->
                        audioItemIds.remove(id)
                        pendingDeletedAudioItemIds.add(id)
                    }
                    queueAudioItemsRefresh()
                } else if (event.isCreate()) {
                    event.entities.values.forEach { item ->
                        if (audioItemIds.add(item.id)) {
                            pendingCreatedAudioItems.add(item)
                        }
                    }
                    queueAudioItemsRefresh()
                } else {
                    queueAudioItemsRefresh()
                }
            }

        private val artistCatalogSubscription =
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

        private val albumSubscription =
            albumPublisher.subscribe(CREATE, UPDATE, DELETE) { queueCatalogRefresh() }

        private val genreIndexSubscription =
            genreIndexPublisher.subscribe(CREATE, UPDATE, DELETE) { queueCatalogRefresh() }

        init {
            guardedRegister(ObservableAudioItem::class.java, repository)

            // Populate fxAggregateList from existing items. Wire metadataIO back-refs first so that
            // any mutation event fired during the subscription pass (subscribeExistingItems) finds a
            // non-null delegate on every rehydrated item.
            val initialAudioItems = toList()
            initialAudioItems.forEach { item ->
                if (item is FXAudioItem) item.metadataIO = metadataIO
            }
            subscribeExistingItems()
            audioItemIds.addAll(initialAudioItems.map { it.id })
            if (initialAudioItems.isNotEmpty()) {
                Platform.runLater {
                    audioItems.addAll(initialAudioItems)
                }
            }

            // Items already present at construction (reload from a saved repository) have their catalogs
            // built by the registry projections during initialization — before the catalog subscriptions
            // above are wired — so those initial catalogs never reach the subscriptions. Pull them
            // directly from the registries on the FX thread (the FX projections build their catalogs
            // on the FX thread; this runLater is enqueued after those build tasks, so the catalogs
            // exist when it runs). Subsequent CRUD changes flow through the subscriptions as before.
            Platform.runLater {
                observableArtistCatalogRegistry.forEach { artistCatalogBacking.add(it) }
                refreshCatalogProperties()
            }

            // Subscribe to the player events to update the play count
            playerSubscriber.addOnNextEventAction(PLAYED) { event ->
                val audioItem = event.audioItem
                if (audioItem is FXAudioItem) {
                    audioItem.incrementPlayCount()
                    logger.trace { "Play count of audio item with id ${audioItem.id} increased to ${audioItem.playCount}" }
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
                    if (closed.get()) return@runLater
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
        }

        private fun hasPendingAudioItemRefresh(): Boolean =
            pendingCreatedAudioItems.isNotEmpty() || pendingDeletedAudioItemIds.isNotEmpty()

        private fun queueCatalogRefresh() {
            catalogRefreshRequested.set(true)
            if (!catalogRefreshQueued.compareAndSet(false, true)) {
                return
            }
            ReactiveScope.flowScope.launch {
                delay(FX_REFRESH_DEBOUNCE_MILLIS.milliseconds)
                Platform.runLater {
                    if (closed.get()) return@runLater
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
            // Apply only the changed entries to each FX collection rather than clearing and
            // rebuilding it wholesale. A full clear+re-add (or setAll) re-fires every bound listener
            // on every tick, so during a large import — where this debounced refresh fires repeatedly
            // against steadily-growing collections — the listener/skin work aggregates to O(n^2) of
            // FX-thread cost. Reconciling in place still scans the current snapshot (O(current size)
            // per tick), but emits only the add/remove changes that actually occurred, so bound
            // ListView/TableView consumers do O(delta) work per tick and keep selection/scroll state.
            // That frees the FX thread for the catalog projections' own builds so they converge in time.
            observableArtistCatalogSet.retainAll(artistCatalogBacking)
            observableArtistCatalogSet.addAll(artistCatalogBacking)
            syncOrderedList(observableAlbumList, observableAlbumRegistry.orderedValues())
            syncOrderedList(observableGenreIndexList, observableGenreIndexRegistry.orderedValues())
        }

        /**
         * Mutates [target] in place so it equals [source] (order included), applying only the
         * differences. Entries absent from [source] are removed, then each position that differs has
         * the [source] entry inserted, so a steadily-growing ordered projection fires only the
         * add/remove changes that actually occurred rather than a whole-list replacement each tick.
         */
        private fun <T> syncOrderedList(target: ObservableList<T>, source: List<T>) {
            if (target == source) {
                return
            }
            val sourceContents = HashSet(source)
            target.retainAll(sourceContents)
            var index = 0
            while (index < source.size) {
                if (index >= target.size) {
                    target.add(source[index])
                } else if (target[index] != source[index]) {
                    target.add(index, source[index])
                }
                index++
            }
            while (target.size > source.size) {
                target.removeAt(target.size - 1)
            }
        }

        /**
         * Closes this library idempotently, cancelling all event subscriptions. The closed flag is set
         * first so any in-flight debounce [Platform.runLater] callbacks observe it and no-op instead of
         * mutating the observable state after close; the debounce coroutines themselves are short-lived
         * and release their reference to this library as soon as they run. The base-class subscriptions
         * are cancelled via [cancelBaseSubscriptions] rather than `super.close()` to prevent the base CAS
         * from swallowing the teardown body when this override has already set the flag. Only the first
         * call performs teardown; subsequent calls return immediately.
         */
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            cancelBaseSubscriptions()
            internalSubscription.cancel()
            artistCatalogSubscription.cancel()
            albumSubscription.cancel()
            genreIndexSubscription.cancel()
            conditionalDeregister(ObservableAudioItem::class.java, repository)
        }

        override fun clear() {
            super.clear()
            audioItemIds.clear()
            pendingCreatedAudioItems.clear()
            pendingDeletedAudioItemIds.clear()
            artistCatalogBacking.clear()
            // Empty the observable collections up front so bindings do not show stale items or
            // catalog buckets during the debounce window before the queued refresh runs.
            Platform.runLater {
                if (closed.get()) return@runLater
                audioItems.clear()
                observableArtistCatalogSet.clear()
                observableAlbumList.clear()
                observableGenreIndexList.clear()
            }
            queueCatalogRefresh()
        }

        override fun createFromFile(audioItemPath: Path): FXAudioItem {
            checkOpen()
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
            // Dedup by physical identity: if an item with the same fileName-duration-bitRate key
            // already exists, return it without allocating a new id or adding a duplicate.
            val candidateUniqueId =
                buildString {
                    append(audioItemPath.fileName.toString().replace(' ', '_'))
                    append("-${metadata.duration.toSeconds()}")
                    append("-${metadata.bitRate}")
                }
            val existing = findByUniqueId(candidateUniqueId)
            if (existing.isPresent) return existing.get() as FXAudioItem
            return FXAudioItem(audioItemPath, newId(), metadata).also { fxAudioItem ->
                add(fxAudioItem)
                logger.trace { "New ObservableAudioItem was created from file $audioItemPath with id ${fxAudioItem.id}" }
            }
        }

        override fun add(entity: ObservableAudioItem): Boolean {
            checkOpen()
            if (entity is FXAudioItem) {
                val existingMetadataIO = entity.metadataIO
                if (existingMetadataIO != null && existingMetadataIO !== metadataIO) {
                    logger.warn {
                        "Audio item ${entity.id} was created by a different library instance. " +
                            "Re-wiring its metadata delegate to this library."
                    }
                }
                entity.metadataIO = metadataIO
            }
            return super.add(entity)
        }

        override fun toString() = "FXAudioLibrary(audioItemsCount=${size()})"
    }