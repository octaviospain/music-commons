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
 * Abstract base for album registries backed by a lirp [ObservableProjection].
 *
 * Both the core and FX registries project the audio-item repository into one [Album] per
 * album; they differ only in the concrete projection they build. This base owns the shared
 * behavior: it subscribes to the projection's per-entry changes and republishes them as
 * [albumPublisher] CRUD events (a null old value is a Create, a null new value is a Delete,
 * and both-present is an Update), exposes album query methods over the live projection, and
 * releases the projection on [close]. Subclasses supply the projection via [observeAlbumChanges].
 *
 * @param I The type of audio items stored in albums
 * @param AE The concrete album type managed by this registry
 * @param publisherName Name for the event publisher, used in logging
 */
abstract class AlbumRegistryBase<I, AE>(private val publisherName: String = "AlbumRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I>, AE : ReactiveAlbum<AE, I>, AE : Comparable<AE> {

    private val log = KotlinLogging.logger {}

    /** CRUD event publisher for album changes — exposed to [AudioLibraryBase] consumers. */
    val albumPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<AlbumDetails, AE>> =
        FlowEventPublisher<CrudEvent.Type, CrudEvent<AlbumDetails, AE>>(publisherName).also {
            it.activateEvents(CrudEvent.Type.CREATE, CrudEvent.Type.UPDATE, CrudEvent.Type.DELETE)
        }

    protected abstract val projection: ObservableProjection<AlbumDetails, AE>

    private var entriesChangedHandle: AutoCloseable? = null

    /**
     * Adopts [projection] as the live album lookup and republishes its per-entry changes as
     * album CRUD events. Subclasses call this once from their constructor with the projection
     * they build. Registration replays the current albums as Create events for items already
     * present in the repository.
     */
    protected fun observeAlbumChanges(projection: ObservableProjection<AlbumDetails, AE>) {
        entriesChangedHandle =
            projection.addOnEntriesChangedListener { changes ->
                for ((album, oldAlbum, newAlbum) in changes) {
                    when {
                        oldAlbum == null && newAlbum != null -> {
                            albumPublisher.emitAsync(Create(newAlbum))
                            log.debug { "Album created for ${album.name}" }
                        }
                        oldAlbum != null && newAlbum != null -> {
                            albumPublisher.emitAsync(Update(newAlbum, oldAlbum))
                            log.debug { "Album updated for ${album.name}" }
                        }
                        oldAlbum != null && newAlbum == null -> {
                            albumPublisher.emitAsync(Delete(oldAlbum))
                            log.debug { "Album deleted for ${album.name}" }
                        }
                    }
                }
            }
    }

    /** Returns the album for the given album details, or empty if none exists. */
    fun findById(album: AlbumDetails): Optional<AE> = Optional.ofNullable(projection[album])

    /**
     * Returns the first album whose name contains [albumName] (case-insensitive), or empty if none
     * matches. A blank [albumName] returns empty rather than matching an arbitrary album.
     *
     * Album buckets are keyed by full [AlbumDetails] value, so several distinct albums may share a
     * name (differing in label, year, or album artist). When more than one matches, the bucket
     * returned is not deterministic; use [findById] with the exact [AlbumDetails] to address a
     * specific album unambiguously.
     */
    fun findFirst(albumName: String): Optional<AE> {
        if (albumName.isBlank()) return Optional.empty()
        return Optional.ofNullable(
            projection.entries.firstOrNull {
                it.key.name.lowercase().contains(albumName.lowercase())
            }?.value
        )
    }

    /**
     * Returns the first album matching [predicate], or empty if none matches.
     */
    fun findFirst(predicate: (AE) -> Boolean): Optional<AE> =
        Optional.ofNullable(projection.values.firstOrNull(predicate))

    /** Iterates all current album values. */
    fun forEach(action: (AE) -> Unit) = projection.values.forEach(action)

    /** Returns the number of albums. */
    fun size(): Int = projection.size

    /** Returns `true` if there are no albums. */
    val isEmpty: Boolean get() = projection.isEmpty()

    /** Returns `true` if any album satisfies [predicate]. */
    fun contains(predicate: (AE) -> Boolean): Boolean = projection.values.any(predicate)

    /** Releases the projection subscription and the projection. Called when the owning library is closed. */
    open fun close() {
        entriesChangedHandle?.close()
        projection.close()
        log.debug { "$publisherName closed" }
    }

    override fun toString() = "${this::class.simpleName}(numberOfAlbums=${projection.size})"
}