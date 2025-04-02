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

    val artistsInvolvedProperty: ReadOnlySetProperty<String>

    val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime>

    val dateOfCreationProperty: ReadOnlyProperty<LocalDateTime>

    val playCountProperty: ReadOnlyIntegerProperty
}