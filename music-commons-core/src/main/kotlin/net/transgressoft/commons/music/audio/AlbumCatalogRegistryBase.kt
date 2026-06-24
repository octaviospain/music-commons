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
 * Abstract base for album catalog registries backed by a lirp [ObservableProjection].
 *
 * Both the core and FX registries project the audio-item repository into one album catalog per
 * album; they differ only in the concrete projection they build. This base owns the shared
 * behavior: it subscribes to the projection's per-entry changes and republishes them as
 * [albumCatalogPublisher] CRUD events (a null old value is a Create, a null new value is a Delete,
 * and both-present is an Update), exposes catalog query methods over the live projection, and
 * releases the projection on [close]. Subclasses supply the projection via [observeCatalogChanges].
 *
 * @param I The type of audio items stored in catalogs
 * @param ALC The concrete album catalog type managed by this registry
 * @param publisherName Name for the event publisher, used in logging
 */
abstract class AlbumCatalogRegistryBase<I, ALC>(private val publisherName: String = "AlbumCatalogRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I>, ALC : ReactiveAlbumCatalog<ALC, I>, ALC : Comparable<ALC> {

    private val log = KotlinLogging.logger {}

    /** CRUD event publisher for album catalog changes — exposed to [AudioLibraryBase] consumers. */
    val albumCatalogPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Album, ALC>> =
        FlowEventPublisher<CrudEvent.Type, CrudEvent<Album, ALC>>(publisherName).also {
            it.activateEvents(CrudEvent.Type.CREATE, CrudEvent.Type.UPDATE, CrudEvent.Type.DELETE)
        }

    protected abstract val projection: ObservableProjection<Album, ALC>

    private var entriesChangedHandle: AutoCloseable? = null

    /**
     * Adopts [projection] as the live catalog lookup and republishes its per-entry changes as
     * album catalog CRUD events. Subclasses call this once from their constructor with the
     * projection they build. Registration replays the current catalogs as Create events for items
     * already present in the repository.
     */
    protected fun observeCatalogChanges(projection: ObservableProjection<Album, ALC>) {
        entriesChangedHandle =
            projection.addOnEntriesChangedListener { changes ->
                for ((album, oldCatalog, newCatalog) in changes) {
                    when {
                        oldCatalog == null && newCatalog != null -> {
                            albumCatalogPublisher.emitAsync(Create(newCatalog))
                            log.debug { "Album catalog created for ${album.name}" }
                        }
                        oldCatalog != null && newCatalog != null -> {
                            albumCatalogPublisher.emitAsync(Update(newCatalog, oldCatalog))
                            log.debug { "Album catalog updated for ${album.name}" }
                        }
                        oldCatalog != null && newCatalog == null -> {
                            albumCatalogPublisher.emitAsync(Delete(oldCatalog))
                            log.debug { "Album catalog deleted for ${album.name}" }
                        }
                    }
                }
            }
    }

    /** Returns the catalog for the given album, or empty if none exists. */
    fun findById(album: Album): Optional<ALC> = Optional.ofNullable(projection[album])

    /**
     * Returns the first catalog whose album name contains [albumName] (case-insensitive),
     * or empty if none matches.
     */
    fun findFirst(albumName: String): Optional<ALC> =
        Optional.ofNullable(
            projection.entries.firstOrNull {
                it.key.name.lowercase().contains(albumName.lowercase())
            }?.value
        )

    /**
     * Returns the first catalog matching [predicate], or empty if none matches.
     */
    fun findFirst(predicate: (ALC) -> Boolean): Optional<ALC> =
        Optional.ofNullable(projection.values.firstOrNull(predicate))

    /** Iterates all current catalog values. */
    fun forEach(action: (ALC) -> Unit) = projection.values.forEach(action)

    /** Returns the number of album catalogs. */
    fun size(): Int = projection.size

    /** Returns `true` if there are no album catalogs. */
    val isEmpty: Boolean get() = projection.isEmpty()

    /** Returns `true` if any catalog satisfies [predicate]. */
    fun contains(predicate: (ALC) -> Boolean): Boolean = projection.values.any(predicate)

    /** Releases the projection subscription and the projection. Called when the owning library is closed. */
    open fun close() {
        entriesChangedHandle?.close()
        projection.close()
        log.debug { "$publisherName closed" }
    }

    override fun toString() = "${this::class.simpleName}(numberOfAlbums=${projection.size})"
}