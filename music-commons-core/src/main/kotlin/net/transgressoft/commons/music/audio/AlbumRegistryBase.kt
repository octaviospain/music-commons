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
 *
 * This is a framework base type. It is `public` only because `music-commons-fx` extends it across the
 * module boundary; it is not a consumer extension point and its protected surface is not a stable
 * contract. Extend the provided library facades instead of subclassing this type directly.
 * @since 1.0
 */
public abstract class AlbumRegistryBase<I, AE>(private val publisherName: String = "AlbumRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I>, AE : ReactiveAlbum<AE, I>, AE : Comparable<AE> {

    private val log = KotlinLogging.logger {}

    /**
     * CRUD event publisher for album changes — exposed to [AudioLibraryBase] consumers.
     * @since 1.0
     */
    public val albumPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<AlbumDetails, AE>> =
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
                withLoggingContext("catalogRebuildId" to UUID.randomUUID().toString()) {
                    for ((album, oldAlbum, newAlbum) in changes) {
                        when {
                            oldAlbum == null && newAlbum != null -> {
                                albumPublisher.emitAsync(Create(newAlbum))
                                log.trace { "Album created for ${album.name}" }
                            }
                            oldAlbum != null && newAlbum != null -> {
                                // Use the canonical bucket key (not the representative album.id) as the shared
                                // map key so the Update consistency check passes even when the representative's
                                // year, label, or casing differs between old and new values.
                                albumPublisher.emitAsync(Update(mapOf(album to newAlbum), mapOf(album to oldAlbum)))
                                log.trace { "Album updated for ${album.name}" }
                            }
                            oldAlbum != null && newAlbum == null -> {
                                albumPublisher.emitAsync(Delete(oldAlbum))
                                log.trace { "Album deleted for ${album.name}" }
                            }
                        }
                    }
                    log.debug { "Album registry rebuilt: ${projection.size} entries" }
                }
            }
    }

    /**
     * Returns the album for the given album details, or empty if none exists.
     *
     * The lookup canonicalizes [album] before indexing the projection so that any per-track
     * [AlbumDetails] value (including one with a non-null year or specific albumArtist) resolves
     * to the same bucket as a fully canonical key. This is the correct way to look up a bucket
     * when only a raw track's album metadata is available.
     * @since 1.0
     */
    public fun findById(album: AlbumDetails): Optional<AE> = Optional.ofNullable(projection[album.canonicalKey()])

    /**
     * Returns the first album whose name contains [albumName] (case-insensitive), or empty if none
     * matches. A blank [albumName] returns empty rather than matching an arbitrary album.
     *
     * A normalized album name can map to multiple buckets because canonical identity also includes
     * the compilation-aware album artist. When more than one bucket matches [albumName], the bucket
     * returned is not deterministic; use [findById] with the exact [AlbumDetails] to address a
     * specific album unambiguously.
     * @since 1.0
     */
    public fun findFirst(albumName: String): Optional<AE> {
        if (albumName.isBlank()) return Optional.empty()
        return Optional.ofNullable(
            projection.entries.firstOrNull {
                it.key.name.lowercase().contains(albumName.lowercase())
            }?.value
        )
    }

    /**
     * Returns the first album matching [predicate], or empty if none matches.
     * @since 1.0
     */
    public fun findFirst(predicate: (AE) -> Boolean): Optional<AE> =
        Optional.ofNullable(projection.values.firstOrNull(predicate))

    /**
     * Returns all current album values in bucket order (name, then artist, then year).
     * @since 1.0
     */
    public fun orderedValues(): List<AE> = projection.values.toList()

    /**
     * Iterates all current album values.
     * @since 1.0
     */
    public fun forEach(action: (AE) -> Unit): Unit = projection.values.forEach(action)

    /**
     * Returns the number of albums.
     * @since 1.0
     */
    public fun size(): Int = projection.size

    /**
     * Returns `true` if there are no albums.
     * @since 1.0
     */
    public val isEmpty: Boolean get() = projection.isEmpty()

    /**
     * Returns `true` if any album satisfies [predicate].
     * @since 1.0
     */
    public fun contains(predicate: (AE) -> Boolean): Boolean = projection.values.any(predicate)

    /**
     * Releases the projection subscription and the projection. Called when the owning library is closed.
     * @since 1.0
     */
    public open fun close() {
        entriesChangedHandle?.close()
        projection.close()
        log.debug { "$publisherName closed" }
    }

    override fun toString(): String = "${this::class.simpleName}(numberOfAlbums=${projection.size})"
}