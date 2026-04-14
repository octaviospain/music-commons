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

package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.event.PlayedEventSubscriber
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.READ
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.event.LirpEventSubscription
import net.transgressoft.lirp.event.MutationEvent
import net.transgressoft.lirp.event.StandardCrudEvent.Update
import net.transgressoft.lirp.persistence.Repository
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Base implementation for [ReactiveAudioLibrary] providing artist catalog management.
 *
 * Maintains an [DefaultArtistCatalogRegistry] that automatically synchronizes with repository
 * changes to provide efficient artist-based queries. Delegates repository operations
 * to the underlying repository while managing the artist catalog as a secondary index.
 *
 * The artist catalog registry is kept in sync with the repository through event subscriptions:
 * when audio items are added, updated, or removed from the repository, the corresponding
 * artist catalogs are automatically updated to reflect these changes.
 *
 * @param I The type of audio items stored in this library
 * @param AC The concrete artist catalog type (used by subclasses for specialized implementations)
 */
abstract class AudioLibraryBase<I, AC>(
    protected val repository: Repository<Int, I>,
    protected val observableArtistCatalogRegistry: ArtistCatalogRegistryBase<I, AC>
) : ReactiveAudioLibrary<I, AC>, Repository<Int, I> by repository
    where I : ReactiveAudioItem<I>,
          I : Comparable<I>,
          AC : ReactiveArtistCatalog<AC, I>,
          AC : Comparable<AC> {

    /**
     * Publisher exposing the internal artist catalog registry.
     *
     * This allows consumers to subscribe to artist catalog events without accessing
     * the mutable registry directly, maintaining encapsulation while providing reactive updates.
     */
    override val artistCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Artist, AC>> = observableArtistCatalogRegistry

    private val entitySubscriptions: MutableMap<Int, LirpEventSubscription<in I, MutationEvent.Type, MutationEvent<Int, I>>> =
        ConcurrentHashMap()

    init {
        if (repository.isEmpty.not()) {
            repository.forEach {
                observableArtistCatalogRegistry.addAudioItem(it)
                subscribeMutations(it)
            }
        }
    }

    /**
     * Subscribes to an audio item's mutation events and bridges them to repository UPDATE events.
     *
     * When an audio item's properties change directly (not through repository operations),
     * this subscription detects the change and emits a repository-level UPDATE event,
     * enabling the artist catalog registry to stay synchronized with entity-level mutations.
     */
    private fun subscribeMutations(audioItem: I) {
        val subscription =
            audioItem.subscribe { mutationEvent ->
                repository.emitAsync(Update(mutationEvent.newEntity, mutationEvent.oldEntity))
            }
        entitySubscriptions[audioItem.id] = subscription
    }

    /**
     * Subscription to repository events that keeps the artist catalog registry synchronized.
     *
     * This subscription ensures that:
     * - CREATE events add audio items to their respective artist catalogs and subscribe to mutations
     * - UPDATE events modify catalogs when artist/album/ordering changes occur
     * - DELETE events remove audio items, delete empty catalogs, and unsubscribe from mutations
     * - READ events are ignored (no catalog changes needed)
     *
     */
    private val subscription =
        repository.subscribe { event ->
            when (event.type) {
                CREATE -> {
                    observableArtistCatalogRegistry.addAudioItems(event.entities.values)
                    event.entities.values.forEach { subscribeMutations(it) }
                }
                UPDATE -> {
                    event.entities.values.forEach { updatedAudioItem ->
                        val oldEntity =
                            event.oldEntities.values.firstOrNull { old -> old.id == updatedAudioItem.id }
                                ?: error("Old entity not found for updated one with id ${updatedAudioItem.id}")
                        observableArtistCatalogRegistry.updateCatalog(updatedAudioItem, oldEntity)
                    }
                }
                DELETE -> {
                    observableArtistCatalogRegistry.removeAudioItems(event.entities.values)
                    event.entities.values.forEach { entitySubscriptions.remove(it.id)?.cancel() }
                }
                READ -> {
                    Unit
                }
            }
        }

    private val idCounter = AtomicInteger(1)

    protected fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    override fun createAudioItem(factory: (id: Int) -> I): I = factory(newId()).also { add(it) }

    override val playerSubscriber = PlayedEventSubscriber()

    override fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I> = observableArtistCatalogRegistry.findAlbumAudioItems(artist, albumName)

    override fun getArtistCatalog(artist: Artist): Optional<out AC> = observableArtistCatalogRegistry.findById(artist)

    override fun getArtistCatalog(artistName: String): Optional<out AC> = observableArtistCatalogRegistry.findFirst(artistName)

    override fun containsAudioItemWithArtist(artistName: String) =
        repository.contains {
            it.artistsInvolved.any { artist ->
                artist.name.contentEquals(artistName, true)
            }
        }

    override fun getRandomAudioItemsFromArtist(artist: Artist, size: Short): List<I> =
        repository.search(size.toInt()) { it.artist == artist }.shuffled().toList()

    /**
     * Cancels all event subscriptions managed by this library.
     *
     * Cancels the repository event subscription that synchronizes the artist catalog registry,
     * all per-entity mutation subscriptions, and the player subscriber's subscription.
     */
    override fun close() {
        subscription.cancel()
        entitySubscriptions.values.forEach { it.cancel() }
        entitySubscriptions.clear()
        playerSubscriber.cancelSubscription()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioLibraryBase<*, *>
        return observableArtistCatalogRegistry == that.observableArtistCatalogRegistry
    }

    override fun hashCode() = Objects.hash(observableArtistCatalogRegistry)
}