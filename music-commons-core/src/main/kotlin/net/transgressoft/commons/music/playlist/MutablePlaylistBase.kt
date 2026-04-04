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

package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.lirp.entity.ReactiveEntityBase
import mu.KotlinLogging

/**
 * Abstract base class for top-level mutable playlist implementations.
 *
 * Concrete subclasses declare [audioItems] and [playlists] as lirp mutable aggregate delegates
 * (`mutableAggregateList` / `mutableAggregateSet`), which own their backing ID collections
 * internally and handle lazy entity resolution from [net.transgressoft.lirp.persistence.LirpContext].
 * This class has no dependency on any playlist hierarchy — name uniqueness and hierarchy tracking
 * are the hierarchy's responsibility.
 */
abstract class MutablePlaylistBase<I : ReactiveAudioItem<I>, P : ReactiveAudioPlaylist<I, P>>(
    override val id: Int,
    name: String,
    isDirectory: Boolean
) : ReactiveEntityBase<Int, P>(), ReactiveAudioPlaylist<I, P> {

    private val logger = KotlinLogging.logger {}

    override var isDirectory: Boolean by reactiveProperty(isDirectory)

    override var name: String by reactiveProperty(name)

    abstract override val audioItems: MutableList<I>

    abstract override val playlists: MutableSet<P>

    override fun addAudioItems(audioItems: Collection<I>): Boolean {
        val currentIds = this.audioItems.map { it.id }.toSet()
        val toAdd = audioItems.filter { it.id !in currentIds }
        if (toAdd.isEmpty()) return false
        this.audioItems.addAll(toAdd)
        logger.debug { "Added $toAdd to playlist $uniqueId" }
        return true
    }

    override fun removeAudioItems(audioItems: Collection<I>): Boolean {
        val result = this.audioItems.removeAll(audioItems.toSet())
        if (result)
            logger.debug { "Removed $audioItems from playlist $uniqueId" }
        return result
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIds")
    override fun removeAudioItems(audioItemIds: Collection<Int>): Boolean {
        val idsToRemove = audioItemIds.toSet()
        val toRemove = audioItems.filter { it.id in idsToRemove }
        if (toRemove.isEmpty())
            return false

        audioItems.removeAll(toRemove.toSet())
        logger.debug { "Removed audio items with ids $idsToRemove from playlist $uniqueId" }
        return true
    }

    override fun addPlaylists(playlists: Collection<P>): Boolean {
        val result = playlists.stream().anyMatch { it !in this.playlists }
        this.playlists.addAll(playlists)
        logger.debug { "Added $playlists to playlist $uniqueId" }
        return result
    }

    override fun removePlaylists(playlists: Collection<P>): Boolean {
        val result = playlists.stream().anyMatch { it in this.playlists }
        this.playlists.removeAll(playlists.toSet())
        logger.debug { "Removed $playlists from playlist $uniqueId" }
        return result
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    override fun removePlaylists(playlistIds: Collection<Int>): Boolean {
        val toRemove = this.playlists.filter { it.id in playlistIds }
        val result = toRemove.isNotEmpty()
        if (result) {
            this.playlists.removeAll(toRemove.toSet())
            logger.debug { "Removed playlists with ids $playlistIds from playlist $uniqueId" }
        }
        return result
    }

    override fun clearAudioItems() {
        if (audioItems.isNotEmpty()) {
            val size = audioItems.size
            audioItems.clear()
            logger.debug { "Cleared $size audio items from playlist $uniqueId" }
        }
    }

    override fun clearPlaylists() {
        if (playlists.isNotEmpty()) {
            val size = playlists.size
            playlists.clear()
            logger.debug { "Cleared $size playlists from playlist $uniqueId" }
        }
    }

    private fun <T> formatCollectionWithIndentation(collection: Collection<T>): String {
        if (collection.isEmpty()) return "[]"
        return collection.joinToString(separator = ",\n\t", prefix = "[\n\t", postfix = "\n]") { item ->
            item.toString().split("\n").joinToString("\n\t")
        }
    }

    override fun toString(): String {
        val formattedAudioItems = formatCollectionWithIndentation(audioItems)
        val formattedPlaylists = formatCollectionWithIndentation(playlists)
        return "MutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$formattedAudioItems, playlists=$formattedPlaylists)"
    }
}