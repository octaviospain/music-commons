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
import net.transgressoft.lirp.event.StandardCrudEvent.Create
import net.transgressoft.lirp.event.StandardCrudEvent.Delete
import net.transgressoft.lirp.event.StandardCrudEvent.Update
import net.transgressoft.lirp.persistence.RegistryBase
import mu.KotlinLogging
import java.util.Optional
import java.util.stream.Collectors.partitioningBy

/**
 * Abstract base class for registries managing artist catalogs within an audio library.
 *
 * Provides collection management and CRUD event publishing for artist catalogs.
 * Subclasses define how catalogs are created and how mutation operations are
 * dispatched, enabling different catalog types (e.g., core vs. JavaFX observable).
 *
 * @param I The type of audio items stored in catalogs
 * @param AC The concrete artist catalog type managed by this registry
 * @param publisherName Name for the event publisher, used in logging
 */
abstract class ArtistCatalogRegistryBase<I, AC>(
    publisherName: String = "ArtistCatalogRegistry"
) : RegistryBase<Artist, AC>(publisher = FlowEventPublisher(publisherName))
    where I : ReactiveAudioItem<I>, I : Comparable<I>,
          AC : ReactiveArtistCatalog<AC, I>, AC : Comparable<AC> {

    private val log = KotlinLogging.logger {}

    init {
        activateEvents(CrudEvent.Type.CREATE, CrudEvent.Type.UPDATE, CrudEvent.Type.DELETE)
    }

    /**
     * Creates a new artist catalog for the given artist.
     *
     * @param artist The artist to create a catalog for
     * @return A new catalog instance for the artist
     */
    protected abstract fun createCatalog(artist: Artist): AC

    /**
     * Adds an audio item to the given catalog.
     */
    protected abstract fun AC.addItem(audioItem: I): Boolean

    /**
     * Removes an audio item from the given catalog.
     */
    protected abstract fun AC.removeItem(audioItem: I): Boolean

    /**
     * Re-sorts an audio item within its catalog after ordering properties changed.
     */
    protected abstract fun AC.merge(audioItem: I): Boolean

    /**
     * Checks whether the given catalog contains the specified audio item.
     */
    protected abstract fun AC.containsItem(audioItem: I): Boolean

    /**
     * Creates a deep copy of the given catalog.
     */
    protected abstract fun AC.cloneCatalog(): AC

    internal fun addAudioItem(audioItem: I): Boolean = addAudioItems(listOf(audioItem))

    /**
     * Adds audio items to their respective artist catalogs, creating new catalogs or
     * updating existing ones as needed.
     *
     * For each audio item, this method either creates a new catalog if the
     * artist doesn't exist yet, or adds the item to an existing catalog. Duplicate items
     * (items already in a catalog) are ignored.
     *
     * Events are emitted for newly created and updated catalogs, allowing subscribers to
     * react to changes in the artist collection.
     *
     * @param audioItems The audio items to add
     * @return true if any catalogs were created or updated, false otherwise
     */
    internal fun addAudioItems(audioItems: Collection<I>): Boolean {
        synchronized(this) {
            val catalogsBeforeUpdate = mutableListOf<AC>()

            val addedOrReplacedCatalogs: Map<Boolean, List<AC>> =
                audioItems.stream()
                    .filter { audioItem -> entitiesById.any { it.value.containsItem(audioItem) }.not() }
                    .map { audioItem ->
                        val newCatalog = createCatalog(audioItem.artist).apply { addItem(audioItem) }
                        entitiesById.merge(audioItem.artist, newCatalog) { artistCatalog, _ ->
                            val catalogBeforeUpdate = artistCatalog.cloneCatalog()
                            artistCatalog.addItem(audioItem)
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
    internal fun updateCatalog(updatedAudioItem: I, oldAudioItem: I) {
        synchronized(this) {
            if (artistOrAlbumChanged(updatedAudioItem, oldAudioItem)) {
                val removed = removeAudioItem(oldAudioItem)
                val added = addAudioItem(updatedAudioItem)
                check(removed || added) { "Update of an audio item in the catalog is supposed to happen at this point" }

                log.debug { "Artist catalog of ${updatedAudioItem.artist.name} was updated as a result of updating $updatedAudioItem" }
            } else if (audioItemOrderingChanged(updatedAudioItem, oldAudioItem)) {
                val artistCatalog =
                    entitiesById[updatedAudioItem.artist] ?: error(
                        "Artist catalog for ${updatedAudioItem.artistUniqueId()} should exist already at this point"
                    )
                val artistCatalogBeforeUpdate = artistCatalog.cloneCatalog()
                val reordered = artistCatalog.merge(updatedAudioItem)
                if (reordered) {
                    publisher.emitAsync(Update(artistCatalog, artistCatalogBeforeUpdate))
                }
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

    internal fun removeAudioItem(audioItem: I): Boolean = removeAudioItems(listOf(audioItem))

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
    internal fun removeAudioItems(audioItems: Collection<I>): Boolean {
        synchronized(this) {
            val removedCatalogs = mutableListOf<AC>()
            val catalogsBeforeUpdate = mutableListOf<AC>()
            val updatedCatalogs = mutableListOf<AC>()

            audioItems.forEach { audioItem ->
                entitiesById[audioItem.artist]?.let {
                    val oldArtistCatalog = it.cloneCatalog()
                    val wasRemoved = it.removeItem(audioItem)
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

    private fun Collection<AC>.toArtistNames(): List<String> = map { it.artist.name }

    private fun ReactiveAudioItem<I>.artistUniqueId() = ImmutableArtist.id(artist.name, artist.countryCode)

    internal fun findFirst(artistName: String): Optional<AC> =
        Optional.ofNullable(entitiesById.entries.firstOrNull { it.key.name.lowercase().contains(artistName.lowercase()) }?.value)

    internal fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I> = entitiesById[artist]?.albumAudioItems(albumName) ?: emptySet()

    override fun toString() = "${this::class.simpleName}(numberOfArtists=${entitiesById.size})"
}