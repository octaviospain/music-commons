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
 */
abstract class GenreIndexRegistryBase<I, GI>(private val publisherName: String = "GenreIndexRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I>, GI : ReactiveGenreIndex<GI, I>, GI : Comparable<GI> {

    private val log = KotlinLogging.logger {}

    /** CRUD event publisher for genre index changes — exposed to [AudioLibraryBase] consumers. */
    val genreIndexPublisher: LirpEventPublisher<CrudEvent.Type, CrudEvent<Genre, GI>> =
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
                for ((genre, oldIndex, newIndex) in changes) {
                    when {
                        oldIndex == null && newIndex != null -> {
                            genreIndexPublisher.emitAsync(Create(newIndex))
                            log.debug { "Genre index created for ${genre.name}" }
                        }
                        oldIndex != null && newIndex != null -> {
                            genreIndexPublisher.emitAsync(Update(newIndex, oldIndex))
                            log.debug { "Genre index updated for ${genre.name}" }
                        }
                        oldIndex != null && newIndex == null -> {
                            genreIndexPublisher.emitAsync(Delete(oldIndex))
                            log.debug { "Genre index deleted for ${genre.name}" }
                        }
                    }
                }
            }
    }

    /** Returns the index for the given genre, or empty if none exists. */
    fun findById(genre: Genre): Optional<GI> = Optional.ofNullable(projection[genre])

    /**
     * Returns the first index whose genre name contains [genreName] (case-insensitive), or empty if
     * none matches. A blank [genreName] resolves to the no-genre bucket ([Genre.None]) if present,
     * or empty if no untagged items exist.
     */
    fun findFirst(genreName: String): Optional<GI> {
        if (genreName.isBlank()) return Optional.ofNullable(projection[Genre.None])
        return Optional.ofNullable(
            projection.entries.firstOrNull {
                it.key.name.lowercase().contains(genreName.lowercase())
            }?.value
        )
    }

    /**
     * Returns the first index matching [predicate], or empty if none matches.
     */
    fun findFirst(predicate: (GI) -> Boolean): Optional<GI> =
        Optional.ofNullable(projection.values.firstOrNull(predicate))

    /** Iterates all current index values. */
    fun forEach(action: (GI) -> Unit) = projection.values.forEach(action)

    /** Returns the number of genre indexes. */
    fun size(): Int = projection.size

    /** Returns `true` if there are no genre indexes. */
    val isEmpty: Boolean get() = projection.isEmpty()

    /** Returns `true` if any index satisfies [predicate]. */
    fun contains(predicate: (GI) -> Boolean): Boolean = projection.values.any(predicate)

    /** Releases the projection subscription and the projection. Called when the owning library is closed. */
    open fun close() {
        entriesChangedHandle?.close()
        projection.close()
        log.debug { "$publisherName closed" }
    }

    override fun toString() = "${this::class.simpleName}(numberOfGenres=${projection.size})"
}