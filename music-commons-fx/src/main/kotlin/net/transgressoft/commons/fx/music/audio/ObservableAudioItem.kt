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
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import javafx.beans.property.FloatProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.StringProperty
import javafx.scene.image.Image
import java.time.LocalDateTime
import java.util.Optional

/**
 * JavaFX-compatible audio item interface exposing metadata as observable properties.
 *
 * Extends [ReactiveAudioItem] with JavaFX property bindings, enabling automatic UI updates
 * when metadata changes. All mutable fields are accessible both as regular properties and
 * as JavaFX properties for seamless integration with JavaFX data binding.
 */
interface ObservableAudioItem : ReactiveAudioItem<ObservableAudioItem> {

    val titleProperty: StringProperty

    val artistProperty: ObjectProperty<Artist>

    val albumProperty: ObjectProperty<Album>

    val genreProperty: ObjectProperty<Genre>

    val commentsProperty: StringProperty

    val trackNumberProperty: IntegerProperty

    val discNumberProperty: IntegerProperty

    val bpmProperty: FloatProperty

    val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>>

    val artistsInvolvedProperty: ReadOnlySetProperty<Artist>

    val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime>

    val dateOfCreationProperty: ReadOnlyProperty<LocalDateTime>

    val playCountProperty: ReadOnlyIntegerProperty
}