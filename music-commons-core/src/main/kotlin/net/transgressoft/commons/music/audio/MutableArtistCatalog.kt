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

import net.transgressoft.lirp.entity.ReactiveEntityBase
import mu.KotlinLogging
import java.util.*

/**
 * Concrete artist catalog type used internally by the audio library infrastructure.
 *
 * Extends [ReactiveArtistCatalog] as a typed marker for catalog instances managed by
 * the registry. Mutation operations are internal to the concrete implementations.
 *
 * @param I The type of audio items contained in this catalog
 */
interface ArtistCatalog<I : ReactiveAudioItem<I>> : ReactiveArtistCatalog<ArtistCatalog<I>, I>, Comparable<ArtistCatalog<I>>

/**
 * Internal catalog managing all audio items for a single artist.
 *
 * Organizes audio items by album and maintains them sorted by disc and track numbers.
 * This structure enables efficient queries for artist discographies and album-specific
 * track listings while providing thread-safe concurrent access.
 *
 * Compound operations on [audioItemsByAlbumName] are guarded by `synchronized(this)`. The
 * [equals] and [hashCode] methods intentionally omit synchronization because they are only
 * invoked from single-threaded contexts (clone-compare in `mutateAndPublish`, collection
 * lookups during event handling). Adding synchronization to [equals] would introduce lock
 * ordering risks when comparing two catalogs concurrently.
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
                    .values
                    .flatten()
                    .distinctBy(::audioItemIdentity)
                    .groupBy { it.album.name }
                    .mapNotNull { (albumName, audioItems) ->
                        if (audioItems.isEmpty()) {
                            null
                        } else {
                            runCatching { AlbumView(albumName, audioItems) }.getOrNull()
                        }
                    }.toSet()
            }

    private fun audioItemIdentity(audioItem: I): Any =
        if (audioItem.id != UNASSIGNED_ID) {
            audioItem.id
        } else {
            audioItem.uniqueId
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
     * Uses item identity for duplicate detection and comparable ordering only for insertion.
     *
     * @param audioItem The audio item to add
     * @return true if the audio item was added
     */
    internal fun addAudioItem(audioItem: I): Boolean =
        synchronized(this) {
            mutateAndPublish {
                val audioItems = audioItemsByAlbumName.getOrPut(audioItem.album.name) { mutableListOf() }
                if (audioItems.none { isSameAudioItem(it, audioItem) }) {
                    val insertionPoint =
                        audioItems.indexOfFirst { it > audioItem }.let {
                            if (it >= 0) it else audioItems.size
                        }
                    audioItems.add(insertionPoint, audioItem)
                    logger.debug { "AudioItem $audioItem was added to album ${audioItem.album}" }
                    true
                } else {
                    false
                }
            }
        }

    private fun isSameAudioItem(left: I, right: I): Boolean =
        if (left.id != UNASSIGNED_ID && right.id != UNASSIGNED_ID) {
            left.id == right.id
        } else {
            left === right || left.uniqueId == right.uniqueId
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
                val (resolvedAlbumName, audioItems) =
                    audioItemsByAlbumName[albumName]
                        ?.let { albumName to it }
                        ?: audioItemsByAlbumName.entries
                            .firstOrNull { (_, items) ->
                                items.any { isSameAudioItem(it, audioItem) }
                            }
                            ?.let { (key, items) -> key to items }
                        ?: return@mutateAndPublish false

                val removed = audioItems.removeIf { isSameAudioItem(it, audioItem) }

                if (removed) {
                    if (audioItems.isEmpty()) {
                        audioItemsByAlbumName.remove(resolvedAlbumName)
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
            artist == audioItem.artist &&
                (
                    audioItemsByAlbumName[audioItem.album.name]?.any { isSameAudioItem(it, audioItem) }
                        ?: audioItemsByAlbumName.values.any { audioItems ->
                            audioItems.any { isSameAudioItem(it, audioItem) }
                        }
                )
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