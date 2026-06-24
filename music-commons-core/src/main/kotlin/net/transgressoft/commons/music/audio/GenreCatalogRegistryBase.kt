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
import java.util.Optional

/**
 * Abstract base for genre catalog registries backed by a lirp [ObservableProjection].
 *
 * Both the core and FX registries project the audio-item repository into one genre catalog per
 * genre; they differ only in the concrete projection they build. This base owns the shared
 * behavior: it subscribes to the projection's per-entry changes and republishes them as
 * [genreCatalogPublisher] CRUD events (a null old value is a Create, a null new value is a Delete,
 * and both-present is an Update), exposes catalog query methods over the live projection, and
 * releases the projection on [close]. Subclasses supply the projection via [observeCatalogChanges].
 *
 * Because an audio item may belong to multiple genres simultaneously, the multi-key projection
 * places a single item into every genre bucket it belongs to. An item with an empty genres set
 * is placed in no genre bucket.
 *
 * @param I The type of audio items stored in catalogs
 * @param GC The concrete genre catalog type managed by this registry
 * @param publisherName Name for the event publisher, used in logging
 */
abstract class GenreCatalogRegistryBase<I, GC>(private val publisherName: String = "GenreCatalogRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I>, GC : ReactiveGenreCatalog<GC, I>, GC : Comparable<GC> {

    private val log = KotlinLogging.logger {}

    /** CRUD event publisher for genre catalog changes — exposed to [AudioLibraryBase] consumers. */
    val genreCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Genre, GC>> =
        FlowEventPublisher<CrudEvent.Type, CrudEvent<Genre, GC>>(publisherName).also {
            it.activateEvents(CrudEvent.Type.CREATE, CrudEvent.Type.UPDATE, CrudEvent.Type.DELETE)
        }

    protected abstract val projection: ObservableProjection<Genre, GC>

    private var entriesChangedHandle: AutoCloseable? = null

    /**
     * Adopts [projection] as the live catalog lookup and republishes its per-entry changes as
     * genre catalog CRUD events. Subclasses call this once from their constructor with the
     * projection they build. Registration replays the current catalogs as Create events for items
     * already present in the repository.
     */
    protected fun observeCatalogChanges(projection: ObservableProjection<Genre, GC>) {
        entriesChangedHandle =
            projection.addOnEntriesChangedListener { changes ->
                for ((genre, oldCatalog, newCatalog) in changes) {
                    when {
                        oldCatalog == null && newCatalog != null -> {
                            genreCatalogPublisher.emitAsync(Create(newCatalog))
                            log.debug { "Genre catalog created for ${genre.name}" }
                        }
                        oldCatalog != null && newCatalog != null -> {
                            genreCatalogPublisher.emitAsync(Update(newCatalog, oldCatalog))
                            log.debug { "Genre catalog updated for ${genre.name}" }
                        }
                        oldCatalog != null && newCatalog == null -> {
                            genreCatalogPublisher.emitAsync(Delete(oldCatalog))
                            log.debug { "Genre catalog deleted for ${genre.name}" }
                        }
                    }
                }
            }
    }

    /** Returns the catalog for the given genre, or empty if none exists. */
    fun findById(genre: Genre): Optional<GC> = Optional.ofNullable(projection[genre])

    /**
     * Returns the first catalog whose genre name contains [genreName] (case-insensitive),
     * or empty if none matches.
     */
    fun findFirst(genreName: String): Optional<GC> =
        Optional.ofNullable(
            projection.entries.firstOrNull {
                it.key.name.lowercase().contains(genreName.lowercase())
            }?.value
        )

    /**
     * Returns the first catalog matching [predicate], or empty if none matches.
     */
    fun findFirst(predicate: (GC) -> Boolean): Optional<GC> =
        Optional.ofNullable(projection.values.firstOrNull(predicate))

    /** Iterates all current catalog values. */
    fun forEach(action: (GC) -> Unit) = projection.values.forEach(action)

    /** Returns the number of genre catalogs. */
    fun size(): Int = projection.size

    /** Returns `true` if there are no genre catalogs. */
    val isEmpty: Boolean get() = projection.isEmpty()

    /** Returns `true` if any catalog satisfies [predicate]. */
    fun contains(predicate: (GC) -> Boolean): Boolean = projection.values.any(predicate)

    /** Releases the projection subscription and the projection. Called when the owning library is closed. */
    open fun close() {
        entriesChangedHandle?.close()
        projection.close()
        log.debug { "$publisherName closed" }
    }

    override fun toString() = "${this::class.simpleName}(numberOfGenres=${projection.size})"
}