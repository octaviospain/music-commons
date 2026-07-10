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
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import javafx.beans.property.FloatProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyObjectProperty
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
 * @since 1.0
 */
public interface ObservableAudioItem : ReactiveAudioItem<ObservableAudioItem> {

    /**
     * Observable property exposing the track title.
     * @since 1.0
     */
    public val titleProperty: StringProperty

    /**
     * Observable property exposing the primary [Artist] of the track.
     * @since 1.0
     */
    public val artistProperty: ObjectProperty<Artist>

    /**
     * Observable property exposing the [AlbumDetails] the track belongs to.
     * @since 1.0
     */
    public val albumProperty: ObjectProperty<AlbumDetails>

    /**
     * Observable property exposing the set of [Genre]s assigned to the track.
     * @since 1.0
     */
    public val genresProperty: ObjectProperty<Set<Genre>>

    /**
     * Observable property exposing the track's free-text comments field.
     * @since 1.0
     */
    public val commentsProperty: StringProperty

    /**
     * Observable property exposing the track number within its disc.
     * @since 1.0
     */
    public val trackNumberProperty: IntegerProperty

    /**
     * Observable property exposing the disc number within its release.
     * @since 1.0
     */
    public val discNumberProperty: IntegerProperty

    /**
     * Observable property exposing the beats-per-minute value of the track.
     * @since 1.0
     */
    public val bpmProperty: FloatProperty

    /**
     * Observable property exposing the cover image wrapped in an [Optional].
     *
     * Attaching a listener or reading the value for the first time triggers a lazy load of the
     * cover image from the underlying audio file metadata when the item is wired to a library.
     * @since 1.0
     */
    public val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>>

    /**
     * Observable property exposing the full set of [Artist]s involved in the track, derived from title, artist, and album-artist fields.
     * @since 1.0
     */
    public val artistsInvolvedProperty: ReadOnlySetProperty<Artist>

    /**
     * Observable property exposing the date and time the track metadata was last modified.
     * @since 1.0
     */
    public val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime>

    /**
     * Observable property exposing the date and time the track was first added to the library.
     * @since 1.0
     */
    public val dateOfCreationProperty: ReadOnlyObjectProperty<LocalDateTime>

    /**
     * Observable property exposing the number of times the track has been played.
     * @since 1.0
     */
    public val playCountProperty: ReadOnlyIntegerProperty
}