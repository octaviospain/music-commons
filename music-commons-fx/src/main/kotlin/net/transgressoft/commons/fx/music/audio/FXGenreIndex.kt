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

import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.lirp.entity.ReactiveEntityBase
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import mu.KotlinLogging

/**
 * JavaFX implementation of [ObservableGenreIndex] that is built once from a list snapshot.
 *
 * Audio items are held in a flat list delivered by lirp's multi-key projection, which guarantees
 * artist-then-album-then-track ordering and distinctness before this class is constructed. No
 * internal sorting or de-duplication is performed. JavaFX observable properties are populated
 * during construction and kept stable for the lifetime of the instance.
 *
 * This class must be constructed only on the JavaFX Application Thread because it initializes
 * JavaFX properties and calls [SimpleListProperty.setAll] inside its init block. The registry's
 * `fxFactory` parameter guarantees this thread contract.
 */
internal class FXGenreIndex(
    override val genre: Genre,
    audioItems: List<ObservableAudioItem>
) : ObservableGenreIndex,
    Comparable<ObservableGenreIndex>,
    ReactiveEntityBase<Genre, ObservableGenreIndex>() {

    private val logger = KotlinLogging.logger {}

    // Defensive copy so tracks/size/equals/hashCode stay consistent with the snapshotted FX properties
    // even if the caller mutates the supplied list later.
    private val trackList: List<ObservableAudioItem> = audioItems.toList()

    override val id: Genre = genre

    override val uniqueId: String = genre.name

    override val size: Int get() = trackList.size

    override val isEmpty: Boolean get() = trackList.isEmpty()

    override val tracks: List<ObservableAudioItem> get() = trackList

    override val tracksProperty: ReadOnlyListProperty<ObservableAudioItem>
        field = SimpleListProperty(this, "tracks", FXCollections.observableArrayList())

    override val sizeProperty: ReadOnlyIntegerProperty
        field = SimpleIntegerProperty(this, "size", 0)

    private val _emptyProperty =
        ReadOnlyBooleanWrapper(this, "empty", true).apply {
            bind(sizeProperty.isEqualTo(0))
        }
    override val emptyProperty: ReadOnlyBooleanProperty = _emptyProperty.readOnlyProperty

    override val genreProperty: ReadOnlyObjectProperty<Genre>
        field = SimpleObjectProperty(this, "genre", genre)

    init {
        logger.debug { "FXGenreIndex created for ${genre.name}" }
        tracksProperty.setAll(trackList)
        sizeProperty.set(size)
    }

    override fun clone(): FXGenreIndex = FXGenreIndex(genre, trackList.toList())

    override fun compareTo(other: ObservableGenreIndex): Int = this.genre.compareTo(other.genre)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FXGenreIndex) return false
        if (genre != other.genre) return false
        return trackList == other.trackList
    }

    override fun hashCode(): Int = 31 * genre.hashCode() + trackList.hashCode()

    override fun toString() = "FXGenreIndex(genre=$genre, size=$size)"
}