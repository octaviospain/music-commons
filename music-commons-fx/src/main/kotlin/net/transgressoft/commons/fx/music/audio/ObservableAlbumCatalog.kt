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
import net.transgressoft.commons.music.audio.ReactiveAlbumCatalog
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.scene.image.Image
import java.util.Optional

/**
 * JavaFX-compatible album catalog interface exposing catalog data as observable properties.
 *
 * Extends [ReactiveAlbumCatalog] with JavaFX property bindings, enabling automatic UI updates
 * when the catalog contents change. Each catalog covers a single flat bucket of audio items
 * for one album — no sub-grouping by artist or any other axis. All observable property updates
 * are dispatched on the JavaFX Application Thread via `Platform.runLater`, ensuring thread-safe
 * UI binding.
 *
 * @see ReactiveAlbumCatalog
 */
interface ObservableAlbumCatalog :
    ReactiveAlbumCatalog<ObservableAlbumCatalog, ObservableAudioItem>, Comparable<ObservableAlbumCatalog> {

    /**
     * Observable list of all audio items in this album catalog, in natural comparable order.
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
     * Observable album associated with this catalog.
     *
     * @return A read-only object property containing the album
     */
    val albumProperty: ReadOnlyObjectProperty<Album>

    /**
     * Lazily resolved album cover [Image] for this catalog.
     *
     * Resolves the cover from the first audio item in the catalog that carries cover data and
     * caches it softly. No work is done at construction: the property holds an empty [Optional]
     * until cover data is first resolved through [coverImageBytes], and stays empty when no item
     * in the catalog carries a cover. Property updates are dispatched on the JavaFX Application
     * Thread so it is safe to bind directly to JavaFX UI components.
     *
     * @return A read-only object property containing an [Optional] wrapping the resolved [Image],
     * or an empty [Optional] when there is no cover
     */
    val coverProperty: ReadOnlyObjectProperty<Optional<Image>>
}