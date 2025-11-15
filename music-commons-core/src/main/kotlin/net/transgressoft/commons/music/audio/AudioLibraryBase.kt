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

import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.READ
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.EntityChangeEvent
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
 */
abstract class AudioLibraryBase<I : ReactiveAudioItem<I>>(
    protected val repository: Repository<Int, I>
): AudioLibrary<I>, Repository<Int, I> by repository {

    private val artistCatalogRegistry = ArtistCatalogRegistry<I>()

    init {
        if (repository.isEmpty.not()) {
            repository.runForAll { artistCatalogRegistry.addAudioItems(listOf(it)) }
        }
    }

    // TODO #5 figure out how do unsubscribe from the repository when it is closed
    private val subscription =
        repository.subscribe { event ->
            when (event.type) {
                CREATE -> artistCatalogRegistry.addAudioItems(event.entities.values)
                UPDATE -> {
                    event as EntityChangeEvent<Int, I>
                    event.entities.values.forEach { updatedAudioItem ->
                        val oldEntity =
                            event.oldEntities.values.firstOrNull { old -> old.id == updatedAudioItem.id }
                                ?: error("Old entity not found for updated one with id ${updatedAudioItem.id}")
                        artistCatalogRegistry.updateCatalog(updatedAudioItem, oldEntity)
                    }
                }

                DELETE -> artistCatalogRegistry.removeAudioItems(event.entities.values)
                READ -> Unit // Nothing to do here
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

    override fun getArtistCatalog(artist: Artist): Optional<ArtistView<I>> = artistCatalogRegistry.getArtistView(artist)

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
        val that = other as AudioLibraryBase<*>
        return artistCatalogRegistry == that.artistCatalogRegistry
    }

    override fun hashCode() = Objects.hash(artistCatalogRegistry)
}