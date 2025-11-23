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
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.FlowEventPublisher
import net.transgressoft.commons.event.StandardCrudEvent.Create
import net.transgressoft.commons.event.StandardCrudEvent.Delete
import net.transgressoft.commons.event.StandardCrudEvent.Update
import net.transgressoft.commons.persistence.RegistryBase
import mu.KotlinLogging
import java.util.Optional
import java.util.stream.Collectors.partitioningBy

/**
 * Internal registry managing all artist catalogs within an audio library.
 *
 * Maintains a collection of [MutableArtistCatalog] instances, one per unique artist,
 * and automatically synchronizes catalog contents when audio items are added, updated,
 * or removed from the library. This enables efficient artist-based queries and ensures
 * catalog consistency with the underlying audio item collection.
 */
internal class ArtistCatalogRegistry<I>
: RegistryBase<Artist, MutableArtistCatalog<I>>(publisher = FlowEventPublisher("ArtistCatalogRegistry"))
    where I : ReactiveAudioItem<I>, I : Comparable<I> {

    private val log = KotlinLogging.logger {}

    init {
        activateEvents(CREATE, UPDATE, DELETE)
    }

    /**
     * Adds audio items to their respective artist catalogs, creating new catalogs or
     * updating existing ones as needed.
     *
     * For each audio item, this method either creates a new [MutableArtistCatalog] if the
     * artist doesn't exist yet, or adds the item to an existing catalog. Duplicate items
     * (items already in a catalog) are ignored.
     *
     * Events are emitted for newly created and updated catalogs, allowing subscribers to
     * react to changes in the artist collection.
     *
     * @param audioItems The audio items to add
     * @return true if any catalogs were created or updated, false otherwise
     */
    fun addAudioItems(audioItems: Collection<I>): Boolean {
        synchronized(this) {
            val catalogsBeforeUpdate = mutableListOf<MutableArtistCatalog<I>>()

            val addedOrReplacedCatalogs: Map<Boolean, List<MutableArtistCatalog<I>>> =
                audioItems.stream()
                    .filter { audioItem -> entitiesById.any { it.value.containsAudioItem(audioItem) }.not() }
                    .map { audioItem ->
                        entitiesById.merge(audioItem.artist, MutableArtistCatalog(audioItem)) { artistCatalog, _ ->
                            val catalogBeforeUpdate = artistCatalog.copy()
                            artistCatalog.addAudioItem(audioItem)
                            catalogsBeforeUpdate.add(catalogBeforeUpdate)
                            artistCatalog
                        }!!
                    }.collect(partitioningBy { it.size == 1 })

            addedOrReplacedCatalogs[true]?.let { createdCatalogs ->
                if (createdCatalogs.isNotEmpty()) {
                    publisher.emitAsync(Create(createdCatalogs))
                    log.debug { "${createdCatalogs.size} artist catalogs were created" }
                }
            }

            addedOrReplacedCatalogs[false]?.let { updatedCatalogs ->
                if (updatedCatalogs.isNotEmpty()) {
                    publisher.emitAsync(Update(updatedCatalogs, catalogsBeforeUpdate))
                    log.debug { "${updatedCatalogs.size} artist catalogs were updated" }
                }
            }

            return addedOrReplacedCatalogs.isNotEmpty()
        }
    }

    /**
     * Updates catalogs in response to an audio item being modified.
     *
     * This method handles three distinct update scenarios:
     * 1. Artist or album changed: Removes item from old catalog and adds to new one
     * 2. Track/disc number changed: Re-sorts the item within its existing catalog
     * 3. Other metadata changed: No catalog update needed
     *
     * Appropriate events are emitted based on which scenario applies.
     *
     * @param updatedAudioItem The audio item with updated properties
     * @param oldAudioItem The audio item before the update
     */
    fun updateCatalog(updatedAudioItem: I, oldAudioItem: I) {
        synchronized(this) {
            if (artistOrAlbumChanged(updatedAudioItem, oldAudioItem)) {
                val removed = removeAudioItems(listOf(oldAudioItem))
                val added = addAudioItems(listOf(updatedAudioItem))
                check(removed || added) { "Update of an audio item in the catalog is supposed to happen at this point" }

                log.debug { "Artist catalog of ${updatedAudioItem.artist.name} was updated as a result of updating $updatedAudioItem" }
            } else if (audioItemOrderingChanged(updatedAudioItem, oldAudioItem)) {
                val artistCatalog =
                    entitiesById[updatedAudioItem.artist] ?: error(
                        "Artist catalog for ${updatedAudioItem.artistUniqueId()} should exist already at this point"
                    )
                val artistCatalogBeforeUpdate = artistCatalog.copy()
                artistCatalog.mergeAudioItem(updatedAudioItem)
                publisher.emitAsync(Update(artistCatalog, artistCatalogBeforeUpdate))
            }
        }
    }

    private fun artistOrAlbumChanged(updatedAudioItem: I, oldAudioItem: I): Boolean {
        val artistChanged = updatedAudioItem.artist != oldAudioItem.artist
        val albumChanged = updatedAudioItem.album != oldAudioItem.album
        return artistChanged || albumChanged
    }

    private fun audioItemOrderingChanged(updatedAudioItem: I, oldAudioItem: I): Boolean {
        val trackNumberChanged = updatedAudioItem.trackNumber != oldAudioItem.trackNumber
        val discNumberChanged = updatedAudioItem.discNumber != oldAudioItem.discNumber
        return trackNumberChanged || discNumberChanged
    }

    /**
     * Removes audio items from their respective artist catalogs.
     *
     * If removing an item empties a catalog completely, that catalog is deleted and a
     * DELETE event is emitted. If items remain, an UPDATE event is emitted with the
     * modified catalog.
     *
     * @param audioItems The audio items to remove
     * @return true if any catalogs were updated or deleted, false otherwise
     */
    fun removeAudioItems(audioItems: Collection<I>): Boolean {
        synchronized(this) {
            val removedCatalogs = mutableListOf<MutableArtistCatalog<I>>()
            val catalogsBeforeUpdate = mutableListOf<MutableArtistCatalog<I>>()
            val updatedCatalogs = mutableListOf<MutableArtistCatalog<I>>()

            audioItems.forEach { audioItem ->
                entitiesById[audioItem.artist]?.let {
                    val oldArtistCatalog = it.copy()
                    val wasRemoved = it.removeAudioItem(audioItem)
                    if (wasRemoved) {
                        if (it.isEmpty) {
                            removedCatalogs.add(it)
                            entitiesById.remove(audioItem.artist)
                        } else {
                            updatedCatalogs.add(it)
                            catalogsBeforeUpdate.add(oldArtistCatalog)
                        }
                    }
                }
            }

            if (removedCatalogs.isNotEmpty()) {
                publisher.emitAsync(Delete(removedCatalogs))
                log.debug { "Artist catalogs of ${removedCatalogs.toArtistNames()} were deleted as a result of removing $audioItems from them" }
            }

            if (updatedCatalogs.isNotEmpty()) {
                publisher.emitAsync(Update(updatedCatalogs, catalogsBeforeUpdate))
                log.debug { "Audio items $audioItems were removed from artist catalogs of ${updatedCatalogs.toArtistNames()}" }
            }

            return removedCatalogs.isNotEmpty() || updatedCatalogs.isNotEmpty()
        }
    }

    private fun Collection<MutableArtistCatalog<I>>.toArtistNames(): List<String> = map { it.artist.name }

    private fun ReactiveAudioItem<I>.artistUniqueId() = ImmutableArtist.id(artist.name, artist.countryCode)

    fun findFirst(artistName: String): Optional<MutableArtistCatalog<I>> =
        Optional.ofNullable(entitiesById.entries.firstOrNull { it.key.name.lowercase().contains(artistName.lowercase()) }?.value)

    fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I> = entitiesById[artist]?.albumAudioItems(albumName) ?: emptySet()

    override fun toString() = "ArtistCatalogRegistry(numberOfArtists=${entitiesById.size})"
}