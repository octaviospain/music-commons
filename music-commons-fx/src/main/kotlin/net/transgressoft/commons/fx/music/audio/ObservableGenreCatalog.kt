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
import net.transgressoft.commons.music.audio.ReactiveGenreCatalog
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty

/**
 * JavaFX-compatible genre catalog interface exposing catalog data as observable properties.
 *
 * Extends [ReactiveGenreCatalog] with JavaFX property bindings, enabling automatic UI updates
 * when the catalog contents change. Each catalog covers a single flat bucket of audio items
 * for one genre — because an audio item may belong to multiple genres, a single item can
 * appear in multiple genre catalogs simultaneously. All observable property updates are
 * dispatched on the JavaFX Application Thread via `Platform.runLater`, ensuring thread-safe
 * UI binding.
 *
 * @see ReactiveGenreCatalog
 */
interface ObservableGenreCatalog :
    ReactiveGenreCatalog<ObservableGenreCatalog, ObservableAudioItem>, Comparable<ObservableGenreCatalog> {

    /**
     * Observable list of all audio items in this genre catalog, in natural comparable order.
     *
     * @return A read-only list property containing the catalog's audio items
     */
    val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem>

    /**
     * Observable count of audio items in this catalog.
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
     * Observable genre associated with this catalog.
     *
     * @return A read-only object property containing the genre
     */
    val genreProperty: ReadOnlyObjectProperty<Genre>
}