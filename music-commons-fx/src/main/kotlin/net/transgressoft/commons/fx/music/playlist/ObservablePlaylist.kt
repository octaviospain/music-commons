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

interface ObservablePlaylist : ReactiveAudioPlaylist<ObservableAudioItem, ObservablePlaylist> {

    val nameProperty: ReadOnlyStringProperty

    val isDirectoryProperty: ReadOnlyBooleanProperty

    val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem>

    val audioItemsRecursiveProperty: ReadOnlyListProperty<ObservableAudioItem>

    val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist>

    val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>>
}