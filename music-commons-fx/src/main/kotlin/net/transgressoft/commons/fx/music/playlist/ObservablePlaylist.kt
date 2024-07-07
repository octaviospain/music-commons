package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.playlist.ReactiveAudioPlaylist
import javafx.beans.property.*
import javafx.scene.image.Image
import java.util.*

interface ObservablePlaylist : ReactiveAudioPlaylist<ObservableAudioItem, ObservablePlaylist> {

    val nameProperty: ReadOnlyStringProperty

    val isDirectoryProperty: ReadOnlyBooleanProperty

    val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem>

    val audioItemsRecursiveProperty: ReadOnlyListProperty<ObservableAudioItem>

    val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist>

    val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>>
}