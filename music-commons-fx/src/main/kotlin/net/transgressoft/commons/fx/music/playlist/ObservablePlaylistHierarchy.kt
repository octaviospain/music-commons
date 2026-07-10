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

package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.playlist.ReactivePlaylistHierarchy
import javafx.beans.property.ReadOnlySetProperty

/**
 * JavaFX-compatible playlist hierarchy interface exposing playlists as an observable collection.
 *
 * Extends [ReactivePlaylistHierarchy] with concrete JavaFX-observable type parameters and adds
 * a JavaFX property for direct binding to UI components. All playlist changes are reflected
 * in [playlistsProperty] automatically on the JavaFX Application Thread.
 * @since 1.0
 */
public interface ObservablePlaylistHierarchy : ReactivePlaylistHierarchy<ObservableAudioItem, ObservablePlaylist> {

    /**
     * Observable set of all playlists in the hierarchy, suitable for direct JavaFX binding.
     * @since 1.0
     */
    public val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist>
}