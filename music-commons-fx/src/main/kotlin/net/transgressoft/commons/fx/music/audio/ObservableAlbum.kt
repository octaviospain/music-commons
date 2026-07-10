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
import net.transgressoft.commons.music.audio.ReactiveAlbum
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.scene.image.Image
import java.util.Optional

/**
 * JavaFX-compatible album bucket interface exposing album data as observable properties.
 *
 * Extends [ReactiveAlbum] with JavaFX property bindings, enabling automatic UI updates
 * when the bucket contents change. Each bucket covers a single flat collection of audio items
 * for one album — no sub-grouping by artist or any other axis. All observable property updates
 * are dispatched on the JavaFX Application Thread via `Platform.runLater`, ensuring thread-safe
 * UI binding.
 *
 * @see ReactiveAlbum
 * @since 1.0
 */
public interface ObservableAlbum :
    ReactiveAlbum<ObservableAlbum, ObservableAudioItem>, Comparable<ObservableAlbum> {

    /**
     * Observable list of all audio items in this album, in disc-then-track order.
     *
     * @return A read-only list property containing the album's audio items
     * @since 1.0
     */
    public val tracksProperty: ReadOnlyListProperty<ObservableAudioItem>

    /**
     * Observable count of audio items in this album.
     *
     * @return A read-only integer property with the total item count
     * @since 1.0
     */
    public val sizeProperty: ReadOnlyIntegerProperty

    /**
     * Observable boolean indicating whether this album is empty.
     *
     * @return A read-only boolean property that is true when the album has no audio items
     * @since 1.0
     */
    public val emptyProperty: ReadOnlyBooleanProperty

    /**
     * Observable album details associated with this bucket.
     *
     * @return A read-only object property containing the album details
     * @since 1.0
     */
    public val albumProperty: ReadOnlyObjectProperty<AlbumDetails>

    /**
     * Lazily resolved album cover [Image] for this album.
     *
     * Observing this property — binding a listener or reading its value — triggers cover resolution
     * on first access. The owning [FXAlbum] searches the bucket's track list for the first item
     * carrying cover data, caches the bytes softly, and dispatches the decoded [Image] via
     * [coverProperty].set on the JavaFX Application Thread. Subsequent observations hit the latch
     * and return immediately without re-probing the track list.
     *
     * The property holds an empty [Optional] until the resolved [Image] arrives on the FX thread,
     * so the first [get] returns empty and bound listeners receive the [Image] on the next pulse.
     * When no item in the bucket carries a cover the property stays empty and the latch suppresses
     * all further probing.
     *
     * @return A read-only object property containing an [Optional] wrapping the resolved [Image],
     * or an empty [Optional] when there is no cover
     * @since 1.0
     */
    public val coverProperty: ReadOnlyObjectProperty<Optional<Image>>
}