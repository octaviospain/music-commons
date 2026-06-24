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
import net.transgressoft.lirp.event.PropertyChanged
import net.transgressoft.lirp.event.ReactiveMutationEvent
import net.transgressoft.lirp.event.StandardCrudEvent.Update
import net.transgressoft.lirp.persistence.Repository
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Base implementation for [ReactiveAudioLibrary] providing artist, album, and genre catalog management.
 *
 * Manages all three catalog axes through projection-backed registries. Each projection is driven
 * directly from the audio-item repository's CRUD events: adds, removes, and key-changing updates
 * are handled automatically by the projection framework without any manual catalog diffing.
 *
 * A per-item subscription is maintained for each audio item in the library. It re-keys all catalogs
 * only when a catalog-relevant property changes — the item's artist, album, or genres — by emitting
 * a repository-level UPDATE event so each projection's reverse index re-keys the item to its current
 * bucket. Bulk mutations (a [ReactiveMutationEvent] emitted after a batch of changes) also trigger
 * the re-key. Non-key mutations such as title, bpm, or track number are deliberately ignored to
 * avoid catalog projection churn. The repository-level UPDATE is required because
 * [net.transgressoft.lirp.persistence.VolatileRepository] and
 * [net.transgressoft.lirp.persistence.json.JsonFileRepository] do not emit CrudEvent.UPDATE on
 * in-place entity mutation — only SqlRepository does.
 *
 * @param I The type of audio items stored in this library
 * @param AC The concrete artist catalog type
 * @param ALC The concrete album catalog type
 * @param GC The concrete genre catalog type
 */
abstract class AudioLibraryBase<I, AC, ALC, GC>(
    protected val repository: Repository<Int, I>,
    protected val observableArtistCatalogRegistry: ArtistCatalogRegistryBase<I, AC>,
    protected val observableAlbumCatalogRegistry: AlbumCatalogRegistryBase<I, ALC>,
    protected val observableGenreCatalogRegistry: GenreCatalogRegistryBase<I, GC>,
    protected val metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()
) : ReactiveAudioLibrary<I, AC, ALC, GC>, Repository<Int, I> by repository
    where I : ReactiveAudioItem<I>,
          I : Comparable<I>,
          AC : ReactiveArtistCatalog<AC, I>,
          AC : Comparable<AC>,
          ALC : ReactiveAlbumCatalog<ALC, I>,
          ALC : Comparable<ALC>,
          GC : ReactiveGenreCatalog<GC, I>,
          GC : Comparable<GC> {

    /**
     * Publisher exposing the internal artist catalog registry.
     *
     * This allows consumers to subscribe to artist catalog events without accessing
     * the mutable registry directly, maintaining encapsulation while providing reactive updates.
     */
    override val artistCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Artist, AC>> =
        observableArtistCatalogRegistry.artistCatalogPublisher

    /** Publisher exposing the internal album catalog registry for reactive album catalog updates. */
    override val albumCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Album, ALC>> =
        observableAlbumCatalogRegistry.albumCatalogPublisher

    /** Publisher exposing the internal genre catalog registry for reactive genre catalog updates. */
    override val genreCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Genre, GC>> =
        observableGenreCatalogRegistry.genreCatalogPublisher

    private val entitySubscriptions: MutableMap<Int, LirpEventSubscription<in I, MutationEvent.Type, MutationEvent<Int, I>>> =
        ConcurrentHashMap()

    init {
        if (repository.isEmpty.not()) {
            repository.forEach {
                subscribeCatalogKeyChanges(it)
            }
        }
    }

    /**
     * Subscribes to an audio item's mutation events and emits a repository-level UPDATE only when a
     * catalog-relevant property (artist, album, or genres) changes, or when a bulk
     * [ReactiveMutationEvent] is emitted after a batch of changes.
     *
     * Scoping the emission to catalog-key properties keeps all catalog projections from re-keying on
     * non-key mutations (title, bpm, track number, …), which would otherwise cause needless catalog
     * churn. The projection's reverse index re-keys the item to its current bucket from the UPDATE;
     * emitting Update also causes JsonFileRepository to persist the change.
     */
    private fun subscribeCatalogKeyChanges(audioItem: I) {
        entitySubscriptions.remove(audioItem.id)?.cancel()
        val subscription =
            audioItem.subscribe { event ->
                val isCatalogRelevant =
                    when (event) {
                        is PropertyChanged<*, *, *> ->
                            event.property.name == ReactiveAudioItem<*>::artist.name ||
                                event.property.name == ReactiveAudioItem<*>::album.name ||
                                event.property.name == ReactiveAudioItem<*>::genres.name
                        is ReactiveMutationEvent<*, *> -> true
                        else -> false
                    }
                if (isCatalogRelevant) {
                    // Both args are the same instance on purpose: every projection (single- and multi-key)
                    // re-keys from its own reverse index off the current item, so a stale "old" snapshot
                    // is unnecessary for any axis — Update only needs the current item to trigger the
                    // re-key and persistence.
                    repository.emitAsync(Update(audioItem, audioItem))
                }
            }
        entitySubscriptions[audioItem.id] = subscription
    }

    /**
     * Subscription to repository events that manages per-entity catalog-sync subscriptions.
     *
     * - CREATE: subscribe to artist/album/genres changes for new audio items so the catalogs and persistence stay in sync
     * - UPDATE: catalog updates are handled by the projections via the artist/album/genres subscription above
     * - DELETE: cancel per-entity subscriptions when items are removed
     * - READ, CONFLICT, RECOVERY_FAILED: no action needed
     *
     * The projections themselves subscribe to the same repository (CREATE, UPDATE, DELETE) and
     * maintain the artist, album, and genre catalogs incrementally — no explicit catalog add/remove calls
     * are needed here.
     */
    private val subscription =
        repository.subscribe { event ->
            when (event.type) {
                CREATE -> {
                    event.entities.values.forEach { subscribeCatalogKeyChanges(it) }
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

    override fun getAlbumCatalog(album: Album): Optional<out ALC> = observableAlbumCatalogRegistry.findById(album)

    override fun getAlbumCatalog(albumName: String): Optional<out ALC> = observableAlbumCatalogRegistry.findFirst(albumName)

    override fun containsAudioItemWithAlbum(albumName: String): Boolean =
        repository.contains { it.album.name.contentEquals(albumName, true) }

    override fun getRandomAudioItemsFromAlbum(album: Album, size: Short): List<I> =
        repository.search(size.toInt()) { it.album == album }.shuffled().toList()

    override fun getGenreCatalog(genre: Genre): Optional<out GC> = observableGenreCatalogRegistry.findById(genre)

    override fun getGenreCatalog(genreName: String): Optional<out GC> = observableGenreCatalogRegistry.findFirst(genreName)

    override fun containsAudioItemWithGenre(genreName: String): Boolean =
        repository.contains { it.genres.any { g -> g.name.contentEquals(genreName, true) } }

    override fun getRandomAudioItemsFromGenre(genre: Genre, size: Short): List<I> =
        repository.search(size.toInt()) { it.genres.contains(genre) }.shuffled().toList()

    /**
     * Cancels all event subscriptions managed by this library.
     *
     * Cancels the repository event subscription, all per-entity mutation subscriptions,
     * the player subscriber's subscription, and all catalog registries.
     */
    override fun close() {
        subscription.cancel()
        entitySubscriptions.values.forEach { it.cancel() }
        entitySubscriptions.clear()
        playerSubscriber.cancelSubscription()
        observableArtistCatalogRegistry.close()
        observableAlbumCatalogRegistry.close()
        observableGenreCatalogRegistry.close()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioLibraryBase<*, *, *, *>
        return repository == that.repository &&
            observableArtistCatalogRegistry == that.observableArtistCatalogRegistry &&
            observableAlbumCatalogRegistry == that.observableAlbumCatalogRegistry &&
            observableGenreCatalogRegistry == that.observableGenreCatalogRegistry
    }

    override fun hashCode() =
        Objects.hash(repository, observableArtistCatalogRegistry, observableAlbumCatalogRegistry, observableGenreCatalogRegistry)
}