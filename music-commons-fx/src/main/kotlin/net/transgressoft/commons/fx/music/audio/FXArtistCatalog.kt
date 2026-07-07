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

import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.ReactiveAlbum
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
    // Built once, keyed by full album identity; sorted and de-duplicated. Never mutated after
    // construction, so reads need no lock. Albums sharing a name but differing in label, year, or
    // album artist are distinct buckets, consistent with the album-axis full-value identity model.
    private val audioItemsByAlbum: Map<AlbumDetails, List<ObservableAudioItem>>
) : ObservableArtistCatalog,
    Comparable<ObservableArtistCatalog>,
    ReactiveEntityBase<Artist, ObservableArtistCatalog>() {

    private val logger = KotlinLogging.logger {}

    /**
     * @param artist the artist this catalog represents
     * @param audioItems the snapshot of audio items to build this catalog from
     */
    internal constructor(artist: Artist, audioItems: List<ObservableAudioItem>) : this(artist, buildAlbumMap(audioItems))

    override val albumsProperty: ReadOnlySetProperty<AlbumDetails>
        field = SimpleSetProperty<AlbumDetails>(this, "albums", observableSet())

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
        logger.trace { "FXArtistCatalog created for ${artist.id()}" }
        populateFxProperties()
    }

    private fun populateFxProperties() {
        albumsProperty.clear()

        // The albumAudioItemsProperty contract is name-based, so distinct full-value albums sharing a
        // name aggregate into a single observable list, matching albumAudioItems(name).
        val itemsByName = linkedMapOf<String, MutableList<ObservableAudioItem>>()
        audioItemsByAlbum.forEach { (albumDetails, items) ->
            if (items.isNotEmpty()) {
                albumsProperty.add(albumDetails)
                itemsByName.getOrPut(albumDetails.name) { mutableListOf() }.addAll(items)
            }
        }

        itemsByName.forEach { (albumName, items) ->
            getOrCreateAlbumItemsProperty(albumName).setAll(items)
        }
        albumItemProperties.keys.retainAll(itemsByName.keys)

        sizeProperty.set(size)
        albumCountProperty.set(albumsProperty.size)
    }

    /**
     * Copy constructor for [clone]: copies the other catalog's already-built album map verbatim,
     * preserving its album keys and item order rather than re-keying from the items' current albums.
     */
    internal constructor(other: FXArtistCatalog) : this(
        other.artist,
        other.audioItemsByAlbum.mapValues { (_, items) -> items.toList() }
    )

    override val albums: Set<ReactiveAlbum<*, ObservableAudioItem>>
        get() =
            audioItemsByAlbum
                .asSequence()
                .filter { it.value.isNotEmpty() }
                .map { (albumDetails, items) -> FXAlbum(albumDetails, items) }
                .toSet()

    override fun albumAudioItems(albumName: String): Set<ObservableAudioItem> =
        audioItemsByAlbum
            .asSequence()
            .filter { it.key.name == albumName }
            .flatMap { it.value.asSequence() }
            .toSet()

    override val isEmpty: Boolean
        get() = audioItemsByAlbum.isEmpty()

    override val size: Int
        get() = audioItemsByAlbum.values.sumOf { it.size }

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
        return audioItemsByAlbum == other.audioItemsByAlbum
    }

    override fun hashCode(): Int = 31 * artist.hashCode() + audioItemsByAlbum.hashCode()

    override fun toString() = "FXArtistCatalog(artist=$artist, size=$size)"

    companion object {

        private fun buildAlbumMap(audioItems: List<ObservableAudioItem>): Map<AlbumDetails, List<ObservableAudioItem>> {
            val map = TreeMap<AlbumDetails, MutableList<ObservableAudioItem>>()
            for (audioItem in audioItems) {
                val bucket = map.getOrPut(audioItem.album) { mutableListOf() }
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