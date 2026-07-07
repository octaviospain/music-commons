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

import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.FlowEventPublisher
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.event.StandardCrudEvent.Create
import net.transgressoft.lirp.event.StandardCrudEvent.Delete
import net.transgressoft.lirp.event.StandardCrudEvent.Update
import net.transgressoft.lirp.persistence.projection.ObservableProjection
import mu.KotlinLogging
import mu.withLoggingContext
import java.util.Optional
import java.util.UUID

/**
 * Abstract base for artist catalog registries backed by a lirp [ObservableProjection].
 *
 * Both the core and FX registries project the audio-item repository into one artist catalog per
 * involved artist; they differ only in the concrete projection they build. This base owns the
 * shared behavior: it subscribes to the projection's per-entry changes and republishes them as
 * [artistCatalogPublisher] CRUD events (a null old value is a Create, a null new value is a Delete,
 * and both-present is an Update), exposes catalog query methods over the live projection, and
 * releases the projection on [close]. Subclasses supply the projection via [observeCatalogChanges].
 *
 * @param I The type of audio items stored in catalogs
 * @param AC The concrete artist catalog type managed by this registry
 * @param publisherName Name for the event publisher, used in logging
 */
abstract class ArtistCatalogRegistryBase<I, AC>(private val publisherName: String = "ArtistCatalogRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I>, AC : ReactiveArtistCatalog<AC, I>, AC : Comparable<AC> {

    private val log = KotlinLogging.logger {}

    /** CRUD event publisher for artist catalog changes — exposed to [AudioLibraryBase] consumers. */
    val artistCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Artist, AC>> =
        FlowEventPublisher<CrudEvent.Type, CrudEvent<Artist, AC>>(publisherName).also {
            it.activateEvents(CrudEvent.Type.CREATE, CrudEvent.Type.UPDATE, CrudEvent.Type.DELETE)
        }

    protected abstract val projection: ObservableProjection<Artist, AC>

    private var entriesChangedHandle: AutoCloseable? = null

    /**
     * Adopts [projection] as the live catalog lookup and republishes its per-entry changes as
     * artist catalog CRUD events. Subclasses call this once from their constructor with the
     * projection they build. Registration replays the current catalogs as Create events for items
     * already present in the repository.
     */
    protected fun observeCatalogChanges(projection: ObservableProjection<Artist, AC>) {
        entriesChangedHandle =
            projection.addOnEntriesChangedListener { changes ->
                withLoggingContext("catalogRebuildId" to UUID.randomUUID().toString()) {
                    for ((artist, oldCatalog, newCatalog) in changes) {
                        when {
                            oldCatalog == null && newCatalog != null -> {
                                artistCatalogPublisher.emitAsync(Create(newCatalog))
                                log.trace { "Artist catalog created for ${artist.name}" }
                            }
                            oldCatalog != null && newCatalog != null -> {
                                artistCatalogPublisher.emitAsync(Update(newCatalog, oldCatalog))
                                log.trace { "Artist catalog updated for ${artist.name}" }
                            }
                            oldCatalog != null && newCatalog == null -> {
                                artistCatalogPublisher.emitAsync(Delete(oldCatalog))
                                log.trace { "Artist catalog deleted for ${artist.name}" }
                            }
                        }
                    }
                    log.debug { "Artist catalog rebuilt: ${projection.size} entries" }
                }
            }
    }

    /** Returns the catalog for the given artist, or empty if none exists. */
    fun findById(artist: Artist): Optional<AC> = Optional.ofNullable(projection[artist])

    /**
     * Returns the first catalog whose artist name contains [artistName] (case-insensitive),
     * or empty if none matches.
     */
    fun findFirst(artistName: String): Optional<AC> =
        Optional.ofNullable(
            projection.entries.firstOrNull {
                it.key.name.lowercase().contains(artistName.lowercase())
            }?.value
        )

    /**
     * Returns the first catalog matching [predicate], or empty if none matches.
     */
    fun findFirst(predicate: (AC) -> Boolean): Optional<AC> =
        Optional.ofNullable(projection.values.firstOrNull(predicate))

    /** Returns the audio items for the given artist and album name, or empty set if not found. */
    fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I> =
        projection[artist]?.albumAudioItems(albumName) ?: emptySet()

    /** Iterates all current catalog values. */
    fun forEach(action: (AC) -> Unit) = projection.values.forEach(action)

    /** Returns the number of artist catalogs. */
    fun size(): Int = projection.size

    /** Returns `true` if there are no artist catalogs. */
    val isEmpty: Boolean get() = projection.isEmpty()

    /** Returns `true` if any catalog satisfies [predicate]. */
    fun contains(predicate: (AC) -> Boolean): Boolean = projection.values.any(predicate)

    /** Releases the projection subscription and the projection. Called when the owning library is closed. */
    open fun close() {
        entriesChangedHandle?.close()
        projection.close()
        log.debug { "$publisherName closed" }
    }

    override fun toString() = "${this::class.simpleName}(numberOfArtists=${projection.size})"
}