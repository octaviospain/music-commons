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

package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.AlbumSet
import net.transgressoft.commons.music.audio.AlbumView
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.id
import net.transgressoft.lirp.entity.ReactiveEntityBase
import javafx.application.Platform
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleSetProperty
import javafx.collections.FXCollections
import javafx.collections.FXCollections.observableSet
import mu.KotlinLogging
import java.util.*
import java.util.Collections.synchronizedSortedMap
import java.util.concurrent.ConcurrentHashMap

/**
 * JavaFX implementation of [ObservableArtistCatalog] that owns its audio item state directly.
 *
 * Maintains audio items organized by album in a synchronized sorted map, mirroring the
 * structure of the core [net.transgressoft.commons.music.audio.MutableArtistCatalog].
 * All mutations update both the internal data structure and the JavaFX observable properties,
 * with property updates dispatched on the JavaFX Application Thread via [Platform.runLater].
 * The dispatches intentionally omit error handling because they perform only simple property mutations;
 * any exception indicates a programming error that should surface through the default uncaught exception handler.
 *
 * @param artist The artist this catalog represents
 */
internal class FXArtistCatalog(
    override val artist: Artist
) : ObservableArtistCatalog,
    Comparable<ObservableArtistCatalog>,
    ReactiveEntityBase<Artist, ObservableArtistCatalog>() {

    private val logger = KotlinLogging.logger {}

    private val audioItemsByAlbumName: MutableMap<String, MutableList<ObservableAudioItem>> = synchronizedSortedMap(sortedMapOf())

    private val _albumsProperty = SimpleSetProperty<Album>(this, "albums", observableSet())
    override val albumsProperty: ReadOnlySetProperty<Album> = _albumsProperty

    private val albumItemProperties = ConcurrentHashMap<String, SimpleListProperty<ObservableAudioItem>>()

    private val _sizeProperty = SimpleIntegerProperty(this, "size", 0)
    override val sizeProperty: ReadOnlyIntegerProperty = _sizeProperty

    private val _albumCountProperty = SimpleIntegerProperty(this, "albumCount", 0)
    override val albumCountProperty: ReadOnlyIntegerProperty = _albumCountProperty

    private val _emptyProperty =
        ReadOnlyBooleanWrapper(this, "empty", true).apply {
            bind(_sizeProperty.isEqualTo(0))
        }
    override val emptyProperty: ReadOnlyBooleanProperty = _emptyProperty.readOnlyProperty

    private val _artistProperty = SimpleObjectProperty(this, "artist", artist)
    override val artistProperty: ReadOnlyObjectProperty<Artist> = _artistProperty

    init {
        logger.debug { "FXArtistCatalog created for ${artist.id()}" }
    }

    internal constructor(other: FXArtistCatalog) : this(other.artist) {
        other.audioItemsByAlbumName.forEach { (albumName, audioItems) ->
            audioItemsByAlbumName[albumName] = audioItems.toMutableList()
        }
    }

    override val id: Artist = artist

    override val uniqueId: String = artist.id()

    override val albums: Set<AlbumSet<ObservableAudioItem>>
        get() =
            synchronized(this) {
                audioItemsByAlbumName
                    .map { (albumName, audioItems) ->
                        AlbumView(albumName, audioItems)
                    }.toSet()
            }

    override fun albumAudioItems(albumName: String): Set<ObservableAudioItem> =
        synchronized(this) {
            audioItemsByAlbumName[albumName]?.toSet() ?: emptySet()
        }

    override val isEmpty: Boolean
        get() = audioItemsByAlbumName.isEmpty()

    override val size: Int
        get() =
            synchronized(this) {
                audioItemsByAlbumName.values.sumOf { it.size }
            }

    internal fun addAudioItem(audioItem: ObservableAudioItem): Boolean =
        synchronized(this) {
            mutateAndPublish {
                val audioItems = audioItemsByAlbumName.getOrPut(audioItem.album.name) { mutableListOf() }
                val existingIndex = audioItems.binarySearch(audioItem)

                if (existingIndex < 0) {
                    val insertionPoint = -(existingIndex + 1)
                    audioItems.add(insertionPoint, audioItem)
                    logger.debug { "AudioItem $audioItem was added to album ${audioItem.album}" }
                    Platform.runLater { updateFXProperties() }
                    true
                } else {
                    false
                }
            }
        }

    internal fun removeAudioItem(audioItem: ObservableAudioItem): Boolean =
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
                    Platform.runLater { updateFXProperties() }
                }
                removed
            }
        }

    internal fun containsAudioItem(audioItem: ObservableAudioItem): Boolean =
        synchronized(this) {
            audioItemsByAlbumName[audioItem.album.name]?.contains(audioItem) ?: false
        }

    internal fun mergeAudioItem(audioItem: ObservableAudioItem): Boolean =
        synchronized(this) {
            mutateAndPublish {
                val audioItems = audioItemsByAlbumName[audioItem.album.name] ?: return@mutateAndPublish false

                if (audioItems.size <= 1) {
                    return@mutateAndPublish false
                }

                val currentIndex = audioItems.indexOfFirst { it === audioItem }
                if (currentIndex < 0) {
                    logger.trace { "mergeAudioItem: Item not found in list!" }
                    return@mutateAndPublish false
                }

                audioItems.removeAt(currentIndex)

                val searchResult = audioItems.binarySearch(audioItem)
                val insertionPoint =
                    if (searchResult < 0)
                        -(searchResult + 1)
                    else
                        searchResult

                audioItems.add(insertionPoint, audioItem)

                val reordered = insertionPoint != currentIndex
                if (reordered) {
                    Platform.runLater { updateFXProperties() }
                }
                reordered
            }
        }

    override fun albumAudioItemsProperty(albumName: String): ReadOnlyListProperty<ObservableAudioItem> =
        getOrCreateAlbumItemsProperty(albumName)

    private fun getOrCreateAlbumItemsProperty(albumName: String): SimpleListProperty<ObservableAudioItem> =
        albumItemProperties.getOrPut(albumName) {
            SimpleListProperty(this, "album-$albumName", FXCollections.observableArrayList())
        }

    private fun updateFXProperties() {
        val currentAlbums = albums
        val currentAlbumNames = currentAlbums.map { it.albumName }.toSet()

        _albumsProperty.clear()
        currentAlbums.forEach { albumSet ->
            _albumsProperty.add(albumSet.first().album)
            val prop = getOrCreateAlbumItemsProperty(albumSet.albumName)
            prop.setAll(albumSet.toList())
        }

        albumItemProperties.keys.retainAll(currentAlbumNames)

        _sizeProperty.set(size)
        _albumCountProperty.set(currentAlbums.size)
    }

    override fun clone(): FXArtistCatalog = FXArtistCatalog(this)

    override fun compareTo(other: ObservableArtistCatalog): Int = this.artist.compareTo(other.artist)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FXArtistCatalog) return false
        return artist == other.artist
    }

    override fun hashCode(): Int = artist.hashCode()

    override fun toString() = "FXArtistCatalog(artist=$artist, size=$size)"
}