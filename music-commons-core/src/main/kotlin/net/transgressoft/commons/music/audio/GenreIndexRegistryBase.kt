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
 * Abstract base for genre index registries backed by a lirp [ObservableProjection].
 *
 * Both the core and FX registries project the audio-item repository into one [GenreIndex] per
 * genre; they differ only in the concrete projection they build. This base owns the shared
 * behavior: it subscribes to the projection's per-entry changes and republishes them as
 * [genreIndexPublisher] CRUD events (a null old value is a Create, a null new value is a Delete,
 * and both-present is an Update), exposes index query methods over the live projection, and
 * releases the projection on [close]. Subclasses supply the projection via [observeGenreIndexChanges].
 *
 * Because an audio item may belong to multiple genres simultaneously, the multi-key projection
 * places a single item into every genre bucket it belongs to. An item with an empty genres set
 * is placed in a dedicated no-genre bucket keyed by [Genre.None].
 *
 * @param I The type of audio items stored in indexes
 * @param GI The concrete genre index type managed by this registry
 * @param publisherName Name for the event publisher, used in logging
 *
 * This is a framework base type. It is `public` only because `music-commons-fx` extends it across the
 * module boundary; it is not a consumer extension point and its protected surface is not a stable
 * contract. Extend the provided library facades instead of subclassing this type directly.
 * @since 1.0
 */
public abstract class GenreIndexRegistryBase<I, GI>(private val publisherName: String = "GenreIndexRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I>, GI : ReactiveGenreIndex<GI, I>, GI : Comparable<GI> {

    private val log = KotlinLogging.logger {}

    /**
     * CRUD event publisher for genre index changes — exposed to [AudioLibraryBase] consumers.
     * @since 1.0
     */
    public val genreIndexPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Genre, GI>> =
        FlowEventPublisher<CrudEvent.Type, CrudEvent<Genre, GI>>(publisherName).also {
            it.activateEvents(CrudEvent.Type.CREATE, CrudEvent.Type.UPDATE, CrudEvent.Type.DELETE)
        }

    protected abstract val projection: ObservableProjection<Genre, GI>

    private var entriesChangedHandle: AutoCloseable? = null

    /**
     * Adopts [projection] as the live genre index lookup and republishes its per-entry changes as
     * genre index CRUD events. Subclasses call this once from their constructor with the projection
     * they build. Registration replays the current indexes as Create events for items already
     * present in the repository.
     */
    protected fun observeGenreIndexChanges(projection: ObservableProjection<Genre, GI>) {
        entriesChangedHandle =
            projection.addOnEntriesChangedListener { changes ->
                withLoggingContext("catalogRebuildId" to UUID.randomUUID().toString()) {
                    for ((genre, oldIndex, newIndex) in changes) {
                        when {
                            oldIndex == null && newIndex != null -> {
                                genreIndexPublisher.emitAsync(Create(newIndex))
                                log.trace { "Genre index created for ${genre.name}" }
                            }
                            oldIndex != null && newIndex != null -> {
                                genreIndexPublisher.emitAsync(Update(newIndex, oldIndex))
                                log.trace { "Genre index updated for ${genre.name}" }
                            }
                            oldIndex != null && newIndex == null -> {
                                genreIndexPublisher.emitAsync(Delete(oldIndex))
                                log.trace { "Genre index deleted for ${genre.name}" }
                            }
                        }
                    }
                    log.debug { "Genre index rebuilt: ${projection.size} entries" }
                }
            }
    }

    /**
     * Returns the index for the given genre, or empty if none exists.
     * @since 1.0
     */
    public fun findById(genre: Genre): Optional<GI> = Optional.ofNullable(projection[genre])

    /**
     * Returns the first index whose genre name contains [genreName] (case-insensitive), or empty if
     * none matches. A blank [genreName] resolves to the no-genre bucket ([Genre.None]) if present,
     * or empty if no untagged items exist.
     * @since 1.0
     */
    public fun findFirst(genreName: String): Optional<GI> {
        if (genreName.isBlank()) return Optional.ofNullable(projection[Genre.None])
        return Optional.ofNullable(
            projection.entries.firstOrNull {
                it.key.name.lowercase().contains(genreName.lowercase())
            }?.value
        )
    }

    /**
     * Returns the first index matching [predicate], or empty if none matches.
     * @since 1.0
     */
    public fun findFirst(predicate: (GI) -> Boolean): Optional<GI> =
        Optional.ofNullable(projection.values.firstOrNull(predicate))

    /**
     * Returns all current genre index values in bucket order (Genre natural order, Genre.None first).
     * @since 1.0
     */
    public fun orderedValues(): List<GI> = projection.values.toList()

    /**
     * Iterates all current index values.
     * @since 1.0
     */
    public fun forEach(action: (GI) -> Unit): Unit = projection.values.forEach(action)

    /**
     * Returns the number of genre indexes.
     * @since 1.0
     */
    public fun size(): Int = projection.size

    /**
     * Returns `true` if there are no genre indexes.
     * @since 1.0
     */
    public val isEmpty: Boolean get() = projection.isEmpty()

    /**
     * Returns `true` if any index satisfies [predicate].
     * @since 1.0
     */
    public fun contains(predicate: (GI) -> Boolean): Boolean = projection.values.any(predicate)

    /**
     * Releases the projection subscription and the projection. Called when the owning library is closed.
     * @since 1.0
     */
    public open fun close() {
        entriesChangedHandle?.close()
        projection.close()
        log.debug { "$publisherName closed" }
    }

    override fun toString(): String = "${this::class.simpleName}(numberOfGenres=${projection.size})"
}