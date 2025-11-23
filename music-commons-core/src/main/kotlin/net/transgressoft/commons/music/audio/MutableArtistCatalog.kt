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

import net.transgressoft.commons.entity.ReactiveEntityBase
import mu.KotlinLogging
import java.util.*

/**
 * Concrete type alias for artist catalogs used internally by the library.
 *
 * This interface combines [ReactiveArtistCatalog] with [Comparable] to enable
 * sorting of catalogs. It's the concrete implementation type used throughout
 * the audio library infrastructure.
 */
interface ArtistCatalog<I : ReactiveAudioItem<I>> : ReactiveArtistCatalog<ArtistCatalog<I>, I>, Comparable<ArtistCatalog<I>>

/**
 * Internal catalog managing all audio items for a single artist.
 *
 * Organizes audio items by album and maintains them sorted by disc and track numbers.
 * This structure enables efficient queries for artist discographies and album-specific
 * track listings while providing thread-safe concurrent access.
 */
internal data class MutableArtistCatalog<I>(override val artist: Artist)
: ArtistCatalog<I>, ReactiveArtistCatalog<ArtistCatalog<I>, I>, Comparable<ArtistCatalog<I>>, ReactiveEntityBase<Artist, ArtistCatalog<I>>()
    where I : ReactiveAudioItem<I> {

    private val logger = KotlinLogging.logger {}

    private val audioItemsByAlbumName: MutableMap<String, MutableList<I>> = Collections.synchronizedSortedMap(sortedMapOf())

    init {
        logger.debug { "Artist catalog created for ${artist.id()}" }
    }

    constructor(audioItem: I) : this(audioItem.artist) {
        addAudioItem(audioItem)
    }

    override val id: Artist = artist

    override val uniqueId: String = artist.id()

    override val albums: Set<AlbumSet<I>>
        get() =
            synchronized(audioItemsByAlbumName) {
                audioItemsByAlbumName
                    .map { (albumName, audioItems) ->
                        AlbumView(albumName, audioItems)
                    }.toSet()
            }

    override fun albumAudioItems(albumName: String): Set<I> =
        synchronized(audioItemsByAlbumName) {
            audioItemsByAlbumName[albumName]?.toSet() ?: emptySet()
        }

    override val isEmpty : Boolean
        get() = audioItemsByAlbumName.isEmpty()

    override val size: Int
        get() =
            synchronized(audioItemsByAlbumName) {
                audioItemsByAlbumName.values.sumOf { it.size }
            }

    /**
     * Adds an audio item to this catalog, maintaining sorted order by disc and track number.
     *
     * Uses binary search to find the correct insertion point, ensuring items remain sorted
     * within their album. If the item already exists (based on [Comparable] equality), it
     * is not added again.
     *
     * @param audioItem The audio item to add
     * @return true if the album now contains more than one item, false otherwise
     */
    internal fun addAudioItem(audioItem: I): Boolean {
        synchronized(audioItemsByAlbumName) {
            val audioItems = audioItemsByAlbumName.getOrPut(audioItem.album.name) { mutableListOf() }

            val existingIndex = audioItems.binarySearch(audioItem)
            if (existingIndex >= 0) {
                return audioItems.size > 1
            }

            val insertionPoint = -(existingIndex + 1)
            audioItems.add(insertionPoint, audioItem)
            logger.debug { "AudioItem $audioItem was added to album ${audioItem.album}" }
            return audioItems.size > 1
        }
    }

    /**
     * Removes an audio item from this catalog.
     *
     * Items are compared by ID. If removing the item empties the album completely,
     * that album is removed from the catalog.
     *
     * @param audioItem The audio item to remove
     * @return true if the item was found and removed, false otherwise
     */
    internal fun removeAudioItem(audioItem: I): Boolean {
        synchronized(audioItemsByAlbumName) {
            val albumName = audioItem.album.name
            val audioItems = audioItemsByAlbumName[albumName] ?: return false

            // In the future when operability with audio items created from a different
            // AudioLibrary this comparison should, or could, be based on the uniqueId or object equality
            val removed = audioItems.removeIf { it.id == audioItem.id }

            if (removed) {
                if (audioItems.isEmpty()) {
                    audioItemsByAlbumName.remove(albumName)
                    logger.debug { "Album ${audioItem.album} was removed from artist catalog of $artist" }
                } else {
                    logger.debug { "AudioItem $audioItem was removed from album ${audioItem.album}" }
                }
            }
            return removed
        }
    }

    /**
     * Checks whether this catalog contains the specified audio item.
     *
     * @param audioItem The audio item to check for
     * @return true if the item is in this catalog, false otherwise
     */
    internal fun containsAudioItem(audioItem: I): Boolean =
        synchronized(audioItemsByAlbumName) {
            audioItemsByAlbumName[audioItem.album.name]?.contains(audioItem) == true
        }

    /**
     * Updates an audio item's position in the catalog by removing and re-adding it.
     *
     * This is used when an audio item's ordering properties (track number, disc number)
     * change, requiring it to be repositioned in the sorted list while keeping the same
     * album association.
     *
     * @param audioItem The audio item with updated ordering properties
     */
    internal fun mergeAudioItem(audioItem: I) {
        synchronized(audioItemsByAlbumName) {
            removeAudioItem(audioItem)
            addAudioItem(audioItem)
        }
    }

    override fun compareTo(other: ArtistCatalog<I>): Int = this.artist.compareTo(other.artist)

    override fun clone(): MutableArtistCatalog<I> = copy()

    override fun toString() = "MutableArtistCatalog(artist=$artist, size=$size)"
}