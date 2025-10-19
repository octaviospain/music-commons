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
import net.transgressoft.commons.music.playlist.ReactiveAudioPlaylist
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.ReadOnlyStringProperty
import javafx.scene.image.Image
import java.util.Optional

/**
 * JavaFX-compatible playlist interface exposing playlist data as observable properties.
 *
 * Extends [ReactiveAudioPlaylist] with JavaFX property bindings for automatic UI synchronization.
 * Provides observable collections of audio items and nested playlists, plus a derived cover
 * image property extracted from the first audio item with cover art.
 */
interface ObservablePlaylist : ReactiveAudioPlaylist<ObservableAudioItem, ObservablePlaylist> {

    val nameProperty: ReadOnlyStringProperty

    val isDirectoryProperty: ReadOnlyBooleanProperty

    val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem>

    val audioItemsRecursiveProperty: ReadOnlyListProperty<ObservableAudioItem>

    val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist>

    val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>>
}