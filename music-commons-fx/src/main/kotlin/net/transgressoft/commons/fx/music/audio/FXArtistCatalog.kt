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
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import net.transgressoft.commons.music.audio.id
import net.transgressoft.lirp.entity.ReactiveEntityBase
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
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

/**
 * JavaFX implementation of [ObservableArtistCatalog] that is built once from a list snapshot.
 *
 * Audio items are organized by album, sorted by disc and track number, and de-duplicated at
 * construction time. The internal album map is never mutated after construction, so reads need
 * no synchronization. JavaFX observable properties are populated during construction and kept
 * stable for the lifetime of the instance.
 *
 * The dispatches to observable properties intentionally omit error handling because they perform
 * only simple property mutations; any exception indicates a programming error that should surface
 * through the default uncaught exception handler.
 */
internal class FXArtistCatalog private constructor(
    override val artist: Artist,
    // Built once; sorted and de-duplicated. Never mutated after construction, so reads need no lock.
    private val audioItemsByAlbumName: Map<String, List<ObservableAudioItem>>
) : ObservableArtistCatalog,
    Comparable<ObservableArtistCatalog>,
    ReactiveEntityBase<Artist, ObservableArtistCatalog>() {

    private val logger = KotlinLogging.logger {}

    /**
     * @param artist the artist this catalog represents
     * @param audioItems the snapshot of audio items to build this catalog from
     */
    internal constructor(artist: Artist, audioItems: List<ObservableAudioItem>) : this(artist, buildAlbumMap(audioItems))

    override val albumsProperty: ReadOnlySetProperty<Album>
        field = SimpleSetProperty<Album>(this, "albums", observableSet())

    private val albumItemProperties = ConcurrentHashMap<String, SimpleListProperty<ObservableAudioItem>>()

    override val sizeProperty: ReadOnlyIntegerProperty
        field = SimpleIntegerProperty(this, "size", 0)

    override val albumCountProperty: ReadOnlyIntegerProperty
        field = SimpleIntegerProperty(this, "albumCount", 0)

    private val _emptyProperty =
        ReadOnlyBooleanWrapper(this, "empty", true).apply {
            bind(sizeProperty.isEqualTo(0))
        }
    override val emptyProperty: ReadOnlyBooleanProperty = _emptyProperty.readOnlyProperty

    override val artistProperty: ReadOnlyObjectProperty<Artist>
        field = SimpleObjectProperty(this, "artist", artist)

    override val id: Artist = artist

    override val uniqueId: String = artist.id()

    init {
        logger.debug { "FXArtistCatalog created for ${artist.id()}" }
        populateFxProperties()
    }

    private fun populateFxProperties() {
        val currentAlbums = albums
        val currentAlbumNames = currentAlbums.map { it.albumName }.toSet()

        albumsProperty.clear()
        currentAlbums.forEach { albumSet ->
            albumsProperty.add(albumSet.first().album)
            val prop = getOrCreateAlbumItemsProperty(albumSet.albumName)
            prop.setAll(albumSet.toList())
        }

        albumItemProperties.keys.retainAll(currentAlbumNames)

        sizeProperty.set(size)
        albumCountProperty.set(currentAlbums.size)
    }

    /**
     * Copy constructor for [clone]: copies the other catalog's already-built album map verbatim,
     * preserving its album keys and item order rather than re-keying from the items' current albums.
     */
    internal constructor(other: FXArtistCatalog) : this(
        other.artist,
        other.audioItemsByAlbumName.mapValues { (_, items) -> items.toList() }
    )

    override val albums: Set<AlbumSet<ObservableAudioItem>>
        get() =
            audioItemsByAlbumName.entries
                .asSequence()
                .mapNotNull { (albumName, audioItems) ->
                    if (audioItems.isEmpty()) {
                        null
                    } else {
                        runCatching { AlbumView(albumName, audioItems) }.getOrNull()
                    }
                }.toSet()

    override fun albumAudioItems(albumName: String): Set<ObservableAudioItem> =
        audioItemsByAlbumName[albumName]?.toSet() ?: emptySet()

    override val isEmpty: Boolean
        get() = audioItemsByAlbumName.isEmpty()

    override val size: Int
        get() = audioItemsByAlbumName.values.sumOf { it.size }

    override fun albumAudioItemsProperty(albumName: String): ReadOnlyListProperty<ObservableAudioItem> =
        getOrCreateAlbumItemsProperty(albumName)

    private fun getOrCreateAlbumItemsProperty(albumName: String): SimpleListProperty<ObservableAudioItem> =
        albumItemProperties.getOrPut(albumName) {
            SimpleListProperty(this, "album-$albumName", FXCollections.observableArrayList())
        }

    override fun clone(): FXArtistCatalog = FXArtistCatalog(this)

    override fun compareTo(other: ObservableArtistCatalog): Int = this.artist.compareTo(other.artist)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FXArtistCatalog) return false
        if (artist != other.artist) return false
        return audioItemsByAlbumName == other.audioItemsByAlbumName
    }

    override fun hashCode(): Int = 31 * artist.hashCode() + audioItemsByAlbumName.hashCode()

    override fun toString() = "FXArtistCatalog(artist=$artist, size=$size)"

    companion object {

        private fun buildAlbumMap(audioItems: List<ObservableAudioItem>): Map<String, List<ObservableAudioItem>> {
            val map = TreeMap<String, MutableList<ObservableAudioItem>>()
            for (audioItem in audioItems) {
                val bucket = map.getOrPut(audioItem.album.name) { mutableListOf() }
                if (bucket.none { isSameAudioItem(it, audioItem) }) {
                    val insertionPoint =
                        bucket
                            .indexOfFirst { it > audioItem }
                            .let {
                                if (it >= 0)
                                    it
                                else
                                    bucket.size
                            }
                    bucket.add(insertionPoint, audioItem)
                }
            }
            return map
        }

        private fun isSameAudioItem(left: ObservableAudioItem, right: ObservableAudioItem): Boolean =
            if (left.id != UNASSIGNED_ID && right.id != UNASSIGNED_ID) {
                left.id == right.id
            } else {
                left === right || left.uniqueId == right.uniqueId
            }
    }
}