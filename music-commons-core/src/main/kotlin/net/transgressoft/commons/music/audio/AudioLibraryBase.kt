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

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.READ
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.TransEventPublisher
import net.transgressoft.commons.music.event.PlayedEventSubscriber
import net.transgressoft.commons.persistence.Repository
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Base implementation for [AudioLibrary] providing artist catalog management.
 *
 * Maintains an [ArtistCatalogRegistry] that automatically synchronizes with repository
 * changes to provide efficient artist-based queries. Delegates repository operations
 * to the underlying repository while managing the artist catalog as a secondary index.
 *
 * The artist catalog registry is kept in sync with the repository through event subscriptions:
 * when audio items are added, updated, or removed from the repository, the corresponding
 * artist catalogs are automatically updated to reflect these changes.
 *
 * @param I The type of audio items stored in this library
 * @param AC The concrete artist catalog type (used by subclasses for specialized implementations)
 */
abstract class AudioLibraryBase<I, AC: ReactiveArtistCatalog<in AC, I>>(protected val repository: Repository<Int, I>)
: AudioLibrary<I, ArtistCatalog<I>>, Repository<Int, I> by repository
    where I : ReactiveAudioItem<I>, I : Comparable<I> {

    private val artistCatalogRegistry = ArtistCatalogRegistry<I>()

    /**
     * Publisher exposing the internal artist catalog registry.
     *
     * This allows consumers to subscribe to artist catalog events without accessing
     * the mutable registry directly, maintaining encapsulation while providing reactive updates.
     */
    override val artistCatalogPublisher: TransEventPublisher<CrudEvent.Type, CrudEvent<Artist, ArtistCatalog<I>>> = artistCatalogRegistry

    init {
        if (repository.isEmpty.not()) {
            repository.runForAll { artistCatalogRegistry.addAudioItems(listOf(it)) }
        }
    }

    /**
     * Subscription to repository events that keeps the artist catalog registry synchronized.
     *
     * This subscription ensures that:
     * - CREATE events add audio items to their respective artist catalogs
     * - UPDATE events modify catalogs when artist/album/ordering changes occur
     * - DELETE events remove audio items and delete empty catalogs
     * - READ events are ignored (no catalog changes needed)
     *
     * TODO #5 figure out how do unsubscribe from the repository when it is closed
     */
    private val subscription =
        repository.subscribe { event ->
            when (event.type) {
                CREATE -> {
                    artistCatalogRegistry.addAudioItems(event.entities.values)
                }

                UPDATE -> {
                    event.entities.values.forEach { updatedAudioItem ->
                        val oldEntity =
                            event.oldEntities.values.firstOrNull { old -> old.id == updatedAudioItem.id }
                                ?: error("Old entity not found for updated one with id ${updatedAudioItem.id}")
                        artistCatalogRegistry.updateCatalog(updatedAudioItem, oldEntity)
                    }
                }

                DELETE -> {
                    artistCatalogRegistry.removeAudioItems(event.entities.values)
                }

                READ -> {
                    Unit
                } // Nothing to do here
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

    override val playerSubscriber = PlayedEventSubscriber()

    override fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I> = artistCatalogRegistry.findAlbumAudioItems(artist, albumName)

    override fun getArtistCatalog(artist: Artist): Optional<out ArtistCatalog<I>> = artistCatalogRegistry.findById(artist)

    override fun getArtistCatalog(artistName: String): Optional<out ArtistCatalog<I>> = artistCatalogRegistry.findFirst(artistName)

    override fun containsAudioItemWithArtist(artistName: String) =
        repository.contains {
            it.artistsInvolved.any { artist ->
                artist.name.contentEquals(artistName, true)
            }
        }

    override fun getRandomAudioItemsFromArtist(artist: Artist, size: Short): List<I> =
        repository.search(2) { it.artist == artist }.shuffled().toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioLibraryBase<*, *>
        return artistCatalogRegistry == that.artistCatalogRegistry
    }

    override fun hashCode() = Objects.hash(artistCatalogRegistry)
}