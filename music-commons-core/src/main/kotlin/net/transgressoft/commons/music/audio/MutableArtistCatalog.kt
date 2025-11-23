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
internal class MutableArtistCatalog<I>(override val artist: Artist)
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

    internal constructor(audioItems: List<I>) : this(audioItems.first().artist) {
        audioItems.forEach(::addAudioItem)
    }

    internal constructor(artistCatalog: MutableArtistCatalog<I>) : this(artistCatalog.artist) {
        // Deep copy: create new lists for each album to avoid sharing mutable state,
        // but the audio items themselves are shared references
        artistCatalog.audioItemsByAlbumName.forEach { (albumName, audioItems) ->
            audioItemsByAlbumName[albumName] = audioItems.toMutableList()
        }
    }

    override val id: Artist = artist

    override val uniqueId: String = artist.id()

    override val albums: Set<AlbumSet<I>>
        get() =
            synchronized(this) {
                audioItemsByAlbumName
                    .map { (albumName, audioItems) ->
                        AlbumView(albumName, audioItems)
                    }.toSet()
            }

    override fun albumAudioItems(albumName: String): Set<I> =
        synchronized(this) {
            audioItemsByAlbumName[albumName]?.toSet() ?: emptySet()
        }

    override val isEmpty : Boolean
        get() = audioItemsByAlbumName.isEmpty()

    override val size: Int
        get() =
            synchronized(this) {
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
     * @return true if the audio item was added
     */
    internal fun addAudioItem(audioItem: I): Boolean =
        synchronized(this) {
            mutateAndPublish {
                val audioItems = audioItemsByAlbumName.getOrPut(audioItem.album.name) { mutableListOf() }
                val existingIndex = audioItems.binarySearch(audioItem)

                if (existingIndex < 0) {
                    val insertionPoint = -(existingIndex + 1)
                    audioItems.add(insertionPoint, audioItem)
                    logger.debug { "AudioItem $audioItem was added to album ${audioItem.album}" }
                    true
                } else {
                    false
                }
            }
        }

    /**
     * Removes an audio item from this catalog.
     * If removing the item empties the album completely, that album is removed from the catalog.
     *
     * @param audioItem The audio item to remove
     * @return true if the item was found and removed, false otherwise
     */
    internal fun removeAudioItem(audioItem: I): Boolean =
        synchronized(this) {
            mutateAndPublish {
                val albumName = audioItem.album.name
                val audioItems = audioItemsByAlbumName[albumName] ?: return@mutateAndPublish false

                val removed = audioItems.removeIf { it.uniqueId == audioItem.uniqueId }

                if (removed) {
                    if (audioItems.isEmpty()) {
                        audioItemsByAlbumName.remove(albumName)
                        logger.debug { "Album ${audioItem.album} was removed from artist catalog of $artist" }
                    } else {
                        logger.debug { "AudioItem $audioItem was removed from album ${audioItem.album}" }
                    }
                }
                removed
            }
        }

    /**
     * Checks whether this catalog contains the specified audio item.
     *
     * @param audioItem The audio item to check for
     * @return true if the item is in this catalog, false otherwise
     */
    internal fun containsAudioItem(audioItem: I): Boolean =
        synchronized(this) {
            audioItemsByAlbumName[audioItem.album.name]?.contains(audioItem)
                ?: return@synchronized false
        }

    /**
     * Updates an audio item's position in the catalog if its ordering properties changed.
     *
     * This is used when an audio item's ordering properties (track number, disc number)
     * change, requiring it to be repositioned in the sorted list while keeping the same
     * album association. If the item's position doesn't change (e.g., it's the only item
     * in the catalog), no mutation event is published.
     *
     * @param audioItem The audio item with updated ordering properties
     * @return true if the item's position changed and was reordered, false otherwise
     */
    internal fun mergeAudioItem(audioItem: I): Boolean =
        synchronized(this) {
            mutateAndPublish {
                val audioItems = audioItemsByAlbumName[audioItem.album.name] ?: return@mutateAndPublish false

                if (audioItems.size <= 1) {
                    return@mutateAndPublish false
                }

                // Find the current index of the item by reference equality (not by ID, as multiple items may have UNASSIGNED_ID)
                val currentIndex = audioItems.indexOfFirst { it === audioItem }
                if (currentIndex < 0) {
                    logger.trace { "mergeAudioItem: Item not found in list!" }
                    return@mutateAndPublish false
                }

                audioItems.removeAt(currentIndex)

                // Find where it should be inserted in the sorted list
                val searchResult = audioItems.binarySearch(audioItem)
                val insertionPoint =
                    if (searchResult < 0)
                        -(searchResult + 1)
                    else
                        searchResult

                audioItems.add(insertionPoint, audioItem)

                (insertionPoint != currentIndex)
            }
        }

    override fun compareTo(other: ArtistCatalog<I>): Int = this.artist.compareTo(other.artist)

    override fun clone(): MutableArtistCatalog<I> = MutableArtistCatalog(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MutableArtistCatalog<*>
        if (artist != other.artist) return false
        return audioItemsByAlbumName == other.audioItemsByAlbumName
    }

    override fun hashCode(): Int = 31 * artist.hashCode() + audioItemsByAlbumName.hashCode()

    override fun toString() = "MutableArtistCatalog(artist=$artist, size=$size)"
}