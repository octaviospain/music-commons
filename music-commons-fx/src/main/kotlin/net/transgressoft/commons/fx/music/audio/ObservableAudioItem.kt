package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import javafx.beans.property.*
import javafx.scene.image.Image
import java.time.LocalDateTime
import java.util.*

interface ObservableAudioItem : ReactiveAudioItem<ObservableAudioItem> {

    val titleProperty: StringProperty

    val artistProperty: ObjectProperty<Artist>

    val albumProperty: ObjectProperty<Album>

    val genreNameProperty: StringProperty

    val commentsProperty: StringProperty

    val trackNumberProperty: IntegerProperty

    val discNumberProperty: IntegerProperty

    val bpmProperty: FloatProperty

    val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>>

    val artistsInvolvedProperty: ReadOnlySetProperty<String>

    val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime>

    val dateOfCreationProperty: ReadOnlyProperty<LocalDateTime>
}
