package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyStringProperty
import javafx.scene.image.Image
import java.util.*

interface ObservablePlaylist : MutableAudioPlaylist<ObservableAudioItem> {

    val nameProperty: ReadOnlyStringProperty

    val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem>

    val audioItemsRecursiveProperty: ReadOnlyListProperty<ObservableAudioItem>

    val playlistsProperty: ReadOnlyListProperty<ObservablePlaylist>

    val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>>
}