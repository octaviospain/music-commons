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
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.ReactiveArtistCatalog
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlySetProperty

/**
 * JavaFX-compatible artist catalog interface exposing catalog data as observable properties.
 *
 * Extends [ReactiveArtistCatalog] with JavaFX property bindings, enabling automatic UI updates
 * when the catalog contents change. All observable property updates are dispatched on the
 * JavaFX Application Thread via `Platform.runLater`, ensuring thread-safe UI binding.
 *
 * @see ReactiveArtistCatalog
 */
interface ObservableArtistCatalog :
    ReactiveArtistCatalog<ObservableArtistCatalog, ObservableAudioItem>, Comparable<ObservableArtistCatalog> {

    /**
     * Observable set of albums in this artist's catalog.
     *
     * @return A read-only set property containing the albums
     */
    val albumsProperty: ReadOnlySetProperty<Album>

    /**
     * Returns an observable list of audio items for the specified album.
     *
     * The returned property automatically updates when audio items are added to or
     * removed from the album. If the album does not exist yet, an empty observable
     * list is created and returned, which will be populated if items are later added.
     *
     * @param albumName The name of the album to retrieve items for
     * @return A read-only list property containing the album's audio items
     */
    fun albumAudioItemsProperty(albumName: String): ReadOnlyListProperty<ObservableAudioItem>

    /**
     * Observable count of albums in this artist's catalog.
     *
     * Semantically distinct from [sizeProperty] which counts audio items.
     * This property counts the number of distinct albums.
     *
     * @return A read-only integer property with the album count
     */
    val albumCountProperty: ReadOnlyIntegerProperty

    /**
     * Observable total count of audio items across all albums in this catalog.
     *
     * @return A read-only integer property with the total item count
     */
    val sizeProperty: ReadOnlyIntegerProperty

    /**
     * Observable boolean indicating whether this catalog is empty.
     *
     * @return A read-only boolean property that is true when the catalog has no audio items
     */
    val emptyProperty: ReadOnlyBooleanProperty

    /**
     * Observable artist associated with this catalog.
     *
     * @return A read-only object property containing the artist
     */
    val artistProperty: ReadOnlyObjectProperty<Artist>
}