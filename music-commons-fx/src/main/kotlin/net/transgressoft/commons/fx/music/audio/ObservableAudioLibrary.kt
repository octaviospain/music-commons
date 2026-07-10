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

import net.transgressoft.commons.music.audio.ReactiveAudioLibrary
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlySetProperty

/**
 * JavaFX-compatible audio library interface exposing collections as observable properties for UI binding.
 *
 * Extends [ReactiveAudioLibrary] with concrete JavaFX-observable type parameters and adds
 * JavaFX properties for direct binding to UI components, enabling reactive table views,
 * list views, and other JavaFX controls without manual synchronization.
 *
 * Flat artist, album, and genre sets are derived from the catalog set properties
 * (e.g. `albumsProperty.map { it.album }.toSet()`).
 * @since 1.0
 */
public interface ObservableAudioLibrary :
    ReactiveAudioLibrary<ObservableAudioItem, ObservableArtistCatalog, ObservableAlbum, ObservableGenreIndex> {

    /**
     * Observable list of all audio items in the library, suitable for direct JavaFX binding.
     * @since 1.0
     */
    public val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem>

    /**
     * Boolean property that is true when the library contains no audio items.
     * @since 1.0
     */
    public val emptyLibraryProperty: ReadOnlyBooleanProperty

    /**
     * Observable set of all artist catalogs, each grouping albums and items by artist.
     * @since 1.0
     */
    public val artistCatalogsProperty: ReadOnlySetProperty<ObservableArtistCatalog>

    /**
     * Observable ordered list of all album buckets, each grouping items by album.
     *
     * Albums are ordered by name (blank last), then artist, then year, matching the
     * bucket ordering of the underlying projection. The list is index-addressable.
     * @since 1.0
     */
    public val albumsProperty: ReadOnlyListProperty<ObservableAlbum>

    /**
     * Observable ordered list of all genre indexes, each grouping items by genre.
     *
     * Genre indexes are ordered by [net.transgressoft.commons.music.audio.Genre] natural
     * order, with [net.transgressoft.commons.music.audio.Genre.None] first. The list is
     * index-addressable.
     * @since 1.0
     */
    public val genreIndexesProperty: ReadOnlyListProperty<ObservableGenreIndex>
}