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
import net.transgressoft.lirp.event.CrudEvent.Type.CONFLICT
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.READ
import net.transgressoft.lirp.event.CrudEvent.Type.RECOVERY_FAILED
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
 * Manages the artist catalog through a projection-backed [ArtistCatalogRegistryBase]. The
 * projection is driven directly from the audio-item repository's CRUD events: adds, removes,
 * and key-changing Updates (artist or album change) are handled automatically by the projection
 * framework without any manual catalog diffing.
 *
 * A per-item mutation subscription (the "mutation bridge") is maintained for each audio item in
 * the library. When an item's properties change, the bridge emits a repository-level UPDATE event
 * so the projection's reverse index can re-key the item to its new artist bucket. This bridge is
 * necessary because [net.transgressoft.lirp.persistence.VolatileRepository] and
 * [net.transgressoft.lirp.persistence.json.JsonFileRepository] do not emit CrudEvent.UPDATE on
 * in-place entity mutation — only SqlRepository does. On SqlRepository-backed libraries the bridge
 * causes a redundant but benign double UPDATE; it is kept simple with no repository-type branching.
 *
 * @param I The type of audio items stored in this library
 * @param AC The concrete artist catalog type (used by subclasses for specialized implementations)
 */
abstract class AudioLibraryBase<I, AC>(
    protected val repository: Repository<Int, I>,
    protected val observableArtistCatalogRegistry: ArtistCatalogRegistryBase<I, AC>,
    protected val metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()
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
    override val artistCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Artist, AC>> =
        observableArtistCatalogRegistry.artistCatalogPublisher

    private val entitySubscriptions: MutableMap<Int, LirpEventSubscription<in I, MutationEvent.Type, MutationEvent<Int, I>>> =
        ConcurrentHashMap()

    init {
        if (repository.isEmpty.not()) {
            repository.forEach {
                subscribeMutations(it)
            }
        }
    }

    /**
     * Subscribes to an audio item's mutation events and emits a repository-level UPDATE.
     *
     * This bridge is required so that the artist catalog projection receives an UPDATE event
     * when an audio item's properties change in-place. The projection's reverse index then
     * re-keys the item to its current artist bucket, handling cross-bucket moves and within-bucket
     * re-sorts automatically.
     *
     * Emitting Update also causes JsonFileRepository to persist the change.
     */
    private fun subscribeMutations(audioItem: I) {
        val subscription =
            audioItem.subscribe { _ ->
                // Both args are the same instance on purpose: the multi-key projection re-keys from
                // its own reverse index rather than from a previous snapshot, so the "old" value is
                // unused — Update only needs the current item to trigger the re-key and persistence.
                repository.emitAsync(Update(audioItem, audioItem))
            }
        entitySubscriptions[audioItem.id] = subscription
    }

    /**
     * Subscription to repository events that manages per-entity mutation subscriptions.
     *
     * - CREATE: subscribe to mutation events for new audio items so catalog and persistence stay in sync
     * - UPDATE: catalog updates are handled by the projection via the mutation bridge above
     * - DELETE: cancel per-entity mutation subscriptions when items are removed
     * - READ, CONFLICT, RECOVERY_FAILED: no action needed
     *
     * The projection itself subscribes to the same repository (CREATE, UPDATE, DELETE) and
     * maintains the artist catalog incrementally — no explicit catalog add/remove calls are
     * needed here.
     */
    private val subscription =
        repository.subscribe { event ->
            when (event.type) {
                CREATE -> {
                    event.entities.values.forEach { subscribeMutations(it) }
                }
                UPDATE -> Unit
                DELETE -> {
                    event.entities.values.forEach { entitySubscriptions.remove(it.id)?.cancel() }
                }
                READ, CONFLICT, RECOVERY_FAILED -> Unit
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
        repository.search(size.toInt()) { it.artistsInvolved.contains(artist) }.shuffled().toList()

    /**
     * Cancels all event subscriptions managed by this library.
     *
     * Cancels the repository event subscription, all per-entity mutation subscriptions,
     * the player subscriber's subscription, and the artist catalog registry.
     */
    override fun close() {
        subscription.cancel()
        entitySubscriptions.values.forEach { it.cancel() }
        entitySubscriptions.clear()
        playerSubscriber.cancelSubscription()
        observableArtistCatalogRegistry.close()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioLibraryBase<*, *>
        return observableArtistCatalogRegistry == that.observableArtistCatalogRegistry
    }

    override fun hashCode() = Objects.hash(observableArtistCatalogRegistry)
}