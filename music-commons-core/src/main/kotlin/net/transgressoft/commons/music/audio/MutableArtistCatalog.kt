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

import net.transgressoft.commons.entity.IdentifiableEntity
import net.transgressoft.commons.music.AudioUtils
import mu.KotlinLogging
import java.util.*

/**
 * Internal catalog managing all audio items for a single artist.
 *
 * Organizes audio items by album and maintains them sorted by disc and track numbers.
 * This structure enables efficient queries for artist discographies and album-specific
 * track listings while providing thread-safe concurrent access.
 */
internal data class MutableArtistCatalog<I>(val artist: Artist) : IdentifiableEntity<String> where I : ReactiveAudioItem<I> {

    private val logger = KotlinLogging.logger {}

    private val albums: MutableMap<String, SortedSet<I>> = Collections.synchronizedMap(mutableMapOf())

    init {
        logger.debug { "Artist catalog created for ${artist.id()}" }
    }

    constructor(audioItem: I) : this(audioItem.artist) {
        addAudioItem(audioItem)
    }

    override val id: String = "${artist.name}-${artist.countryCode.name}"

    override val uniqueId: String = "${artist.name}-${artist.countryCode.name}"

    val size: Int
        get() =
            synchronized(albums) {
                albums.values.sumOf { it.size }
            }

    val isEmpty: Boolean
        get() = albums.isEmpty()

    fun addAudioItem(audioItem: I): Boolean {
        synchronized(albums) {
            val audioItems =
                albums.getOrPut(audioItem.album.name) {
                    sortedSetOf(AudioUtils.audioItemTrackDiscNumberComparator())
                }
            val added = audioItems.add(audioItem)
            if (added) {
                logger.debug { "AudioItem $audioItem was added to album ${audioItem.album}" }
            }
            return audioItems.size > 1
        }
    }

    fun removeAudioItem(audioItem: I): Boolean {
        synchronized(albums) {
            val albumName = audioItem.album.name
            val audioItems = albums[albumName] ?: return false
            val removed = audioItems.removeIf { it.id == audioItem.id }

            if (removed) {
                if (audioItems.isEmpty()) {
                    albums.remove(albumName)
                    logger.debug { "Album ${audioItem.album} was removed from artist catalog of $artist" }
                } else {
                    logger.debug { "AudioItem $audioItem was removed from album ${audioItem.album}" }
                }
            }
            return removed
        }
    }

    fun findAlbumAudioItems(albumName: String): Set<I> = synchronized(albums) { albums[albumName]?.toSet() ?: emptySet() }

    fun containsAudioItem(audioItem: I): Boolean = synchronized(albums) { albums[audioItem.album.name]?.contains(audioItem) == true }

    fun mergeAudioItem(audioItem: I) {
        synchronized(albums) {
            removeAudioItem(audioItem)
            addAudioItem(audioItem)
        }
    }

    fun getArtistView(): ArtistView<I> =
        synchronized(albums) {
            albums.entries.map { AlbumView(it.key, it.value.toSet()) }.toSet()
                .let { ArtistView(artist, it) }
        }

    override fun clone(): MutableArtistCatalog<I> = copy()

    override fun toString() = "MutableArtistCatalog(artist=$artist, size=$size)"
}