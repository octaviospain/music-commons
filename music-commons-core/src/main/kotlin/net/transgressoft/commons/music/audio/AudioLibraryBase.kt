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
import net.transgressoft.lirp.event.BatchChanged
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.CONFLICT
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.READ
import net.transgressoft.lirp.event.CrudEvent.Type.RECOVERY_FAILED
import net.transgressoft.lirp.event.CrudEvent.Type.RESTORE
import net.transgressoft.lirp.event.CrudEvent.Type.SOFT_DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.event.LirpEventSubscription
import net.transgressoft.lirp.event.MutationEvent
import net.transgressoft.lirp.event.PropertyChanged
import net.transgressoft.lirp.event.StandardCrudEvent.Update
import net.transgressoft.lirp.persistence.Repository
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
 * bucket. Bulk mutations (a [BatchChanged] emitted after a batch of changes) also trigger
 * the re-key. Non-key mutations such as title, bpm, or track number are deliberately ignored to
 * avoid catalog projection churn. The repository-level UPDATE is required because
 * [net.transgressoft.lirp.persistence.VolatileRepository] and
 * [net.transgressoft.lirp.persistence.json.JsonFileRepository] do not emit CrudEvent.UPDATE on
 * in-place entity mutation — only SqlRepository does.
 *
 * @param I The type of audio items stored in this library
 * @param AC The concrete artist catalog type
 * @param ALC The type of reactive album exposed by this library
 * @param GC The concrete genre catalog type
 */
abstract class AudioLibraryBase<I, AC, ALC, GC>(
    protected val repository: Repository<Int, I>,
    protected val observableArtistCatalogRegistry: ArtistCatalogRegistryBase<I, AC>,
    protected val observableAlbumRegistry: AlbumRegistryBase<I, ALC>,
    protected val observableGenreIndexRegistry: GenreIndexRegistryBase<I, GC>,
    protected val metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()
) : ReactiveAudioLibrary<I, AC, ALC, GC>, Repository<Int, I> by repository
    where I : ReactiveAudioItem<I>,
          I : Comparable<I>,
          AC : ReactiveArtistCatalog<AC, I>,
          AC : Comparable<AC>,
          ALC : ReactiveAlbum<ALC, I>,
          ALC : Comparable<ALC>,
          GC : ReactiveGenreIndex<GC, I>,
          GC : Comparable<GC> {

    private companion object {
        // Upper bound on id-allocation retries; large enough that it is never reached in practice
        // but prevents an infinite loop when the id space is exhausted (e.g. in tests that
        // fill the Int range).
        const val MAX_ID_ALLOCATION_ATTEMPTS = 1_000_000
    }

    /**
     * Publisher exposing the internal artist catalog registry.
     *
     * This allows consumers to subscribe to artist catalog events without accessing
     * the mutable registry directly, maintaining encapsulation while providing reactive updates.
     */
    override val artistCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Artist, AC>> =
        observableArtistCatalogRegistry.artistCatalogPublisher

    /** Publisher exposing the internal album registry for reactive album updates. */
    override val albumPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<AlbumDetails, ALC>> =
        observableAlbumRegistry.albumPublisher

    /** Publisher exposing the internal genre index registry for reactive genre index updates. */
    override val genreIndexPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Genre, GC>> =
        observableGenreIndexRegistry.genreIndexPublisher

    /**
     * Tracks whether this library has been closed. Once set to `true`, mutations and queries throw
     * [IllegalStateException]. Protected so subtypes can read and transition it while external callers
     * cannot reopen a closed library.
     */
    protected val closed = AtomicBoolean(false)

    /**
     * Asserts that this library has not been closed.
     *
     * @throws IllegalStateException if [close] has already been called on this library.
     */
    protected fun checkOpen() {
        check(!closed.get()) { "This audio library has been closed and can no longer be used." }
    }

    private val entitySubscriptions: MutableMap<Int, LirpEventSubscription<in I, MutationEvent.Type, MutationEvent<Int, I>>> =
        ConcurrentHashMap()

    /**
     * Subscribes existing repository items to catalog-key mutation tracking.
     *
     * Subclasses must call this method as the final step of their own initialization, after any
     * per-item back-references (such as a metadata I/O delegate) have been wired. Calling it
     * earlier opens a window where an item mutation event fires before back-references are set,
     * producing a null-reference access on the first catalog-relevant property change.
     */
    protected fun subscribeExistingItems() {
        if (repository.isEmpty.not()) {
            repository.forEach {
                subscribeCatalogKeyChanges(it)
            }
        }
    }

    /**
     * Subscribes to an audio item's mutation events and emits a repository-level UPDATE only when a
     * catalog-relevant property (artist, album, or genres) changes, or when a bulk
     * [BatchChanged] is emitted after a batch of changes.
     *
     * Scoping the emission to catalog-key properties keeps all catalog projections from re-keying on
     * non-key mutations (title, bpm, track number, …), which would otherwise cause needless catalog
     * churn. The projection's reverse index re-keys the item to its current bucket from the UPDATE;
     * emitting Update also causes JsonFileRepository to persist the change.
     *
     * Documented behavior: subscribers must not mutate catalog-relevant properties (artist, album,
     * or genres) during event delivery. Doing so re-triggers the projection rebuild synchronously,
     * causing a re-entrant UPDATE before the current one completes. This leads to redundant
     * re-key cycles and may produce inconsistent intermediate catalog states visible to other
     * subscribers observing the same event sequence.
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
                        is BatchChanged<*, *> -> true
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
     * - READ, CONFLICT, RECOVERY_FAILED, SOFT_DELETE, RESTORE: no action needed
     *
     * SOFT_DELETE and RESTORE never fire here because audio items are not soft-deletable; the
     * branches exist only to keep the dispatch exhaustive over the event-type enum.
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
                READ, CONFLICT, RECOVERY_FAILED, SOFT_DELETE, RESTORE -> Unit
            }
        }

    private val idCounter = AtomicInteger(1)

    protected fun newId(): Int {
        var id: Int
        var attempts = 0
        do {
            check(attempts++ < MAX_ID_ALLOCATION_ATTEMPTS) {
                "Cannot allocate a new audio item id: the id space appears to be exhausted after $MAX_ID_ALLOCATION_ATTEMPTS attempts."
            }
            id = idCounter.getAndIncrement()
            check(id > 0) {
                "Cannot allocate a new audio item id: the positive id space is exhausted (the id counter overflowed Int.MAX_VALUE)."
            }
        } while (contains(id))
        return id
    }

    override fun createAudioItem(factory: (id: Int) -> I): I {
        checkOpen()
        return factory(newId()).also { add(it) }
    }

    override fun add(entity: I): Boolean {
        checkOpen()
        return repository.add(entity)
    }

    override val playerSubscriber = PlayedEventSubscriber()

    override fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I> = observableArtistCatalogRegistry.findAlbumAudioItems(artist, albumName)

    override fun getArtistCatalog(artist: Artist): Optional<out AC> {
        checkOpen()
        return observableArtistCatalogRegistry.findById(artist)
    }

    override fun getArtistCatalog(artistName: String): Optional<out AC> {
        checkOpen()
        return observableArtistCatalogRegistry.findFirst(artistName)
    }

    override fun containsAudioItemWithArtist(artistName: String) =
        repository.contains {
            it.artistsInvolved.any { artist ->
                artist.name.contentEquals(artistName, true)
            }
        }

    override fun getRandomAudioItemsFromArtist(artist: Artist, size: Short): List<I> =
        repository.search(size.toInt()) { it.artistsInvolved.contains(artist) }.shuffled().toList()

    override fun getAlbum(album: AlbumDetails): Optional<out ALC> {
        checkOpen()
        return observableAlbumRegistry.findById(album)
    }

    override fun getAlbum(albumName: String): Optional<out ALC> {
        checkOpen()
        return observableAlbumRegistry.findFirst(albumName)
    }

    override fun containsAudioItemWithAlbum(albumName: String): Boolean =
        repository.contains { it.album.name.contentEquals(albumName, true) }

    override fun getRandomAudioItemsFromAlbum(album: AlbumDetails, size: Short): List<I> =
        repository.search(size.toInt()) { it.album.canonicalKey() == album.canonicalKey() }.shuffled().toList()

    override fun getGenreIndex(genre: Genre): Optional<out GC> {
        checkOpen()
        return observableGenreIndexRegistry.findById(genre)
    }

    override fun getGenreIndex(genreName: String): Optional<out GC> {
        checkOpen()
        return observableGenreIndexRegistry.findFirst(genreName)
    }

    override fun containsAudioItemWithGenre(genreName: String): Boolean {
        // A blank name targets the no-genre bucket: true iff at least one untagged item exists.
        if (genreName.isBlank()) return repository.contains { it.genres.isEmpty() }
        return repository.contains { it.genres.any { g -> g.name.contentEquals(genreName, true) } }
    }

    override fun getRandomAudioItemsFromGenre(genre: Genre, size: Short): List<I> =
        repository.search(size.toInt()) { it.genres.contains(genre) }.shuffled().toList()

    /**
     * Cancels all base-class event subscriptions and closes the catalog registries.
     *
     * Subclasses that take ownership of the idempotency CAS in their own [close] override
     * (to interpose teardown steps before the base teardown runs) should call this method
     * directly rather than delegating to [close], which would no-op because [closed] is already
     * set by the time `super.close()` is reached.
     */
    protected fun cancelBaseSubscriptions() {
        subscription.cancel()
        entitySubscriptions.values.forEach { it.cancel() }
        entitySubscriptions.clear()
        playerSubscriber.cancelSubscription()
        observableArtistCatalogRegistry.close()
        observableAlbumRegistry.close()
        observableGenreIndexRegistry.close()
    }

    /**
     * Closes this library idempotently.
     *
     * The first call sets the closed flag and cancels all event subscriptions: the repository
     * subscription, all per-entity mutation subscriptions, the player subscriber, and all
     * catalog registries. Subsequent calls return immediately without repeating the teardown.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        cancelBaseSubscriptions()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioLibraryBase<*, *, *, *>
        return repository == that.repository &&
            observableArtistCatalogRegistry == that.observableArtistCatalogRegistry &&
            observableAlbumRegistry == that.observableAlbumRegistry &&
            observableGenreIndexRegistry == that.observableGenreIndexRegistry
    }

    override fun hashCode() =
        Objects.hash(repository, observableArtistCatalogRegistry, observableAlbumRegistry, observableGenreIndexRegistry)
}