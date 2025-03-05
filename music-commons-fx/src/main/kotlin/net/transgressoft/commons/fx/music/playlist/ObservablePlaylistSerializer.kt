package net.transgressoft.commons.fx.music.playlist

import javafx.beans.property.*
import javafx.scene.image.Image
import net.transgressoft.commons.EntityChangeEvent
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.playlist.AudioPlaylistSerializerBase
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Flow

object ObservablePlaylistSerializer : AudioPlaylistSerializerBase<ObservableAudioItem, ObservablePlaylist>() {
    @Suppress("UNCHECKED_CAST")
    override fun createInstance(propertiesList: List<Any?>): ObservablePlaylist =
        DummyPlaylist(
            propertiesList[0] as Int,
            propertiesList[1] as Boolean,
            propertiesList[2] as String,
            propertiesList[3] as List<ObservableAudioItem>,
            propertiesList[4] as Set<ObservablePlaylist>
        )
}

internal class DummyPlaylist(
    override val id: Int,
    override var isDirectory: Boolean = false,
    override var name: String = "",
    override val audioItems: List<ObservableAudioItem> = emptyList(),
    override val playlists: Set<ObservablePlaylist> = emptySet(),
    override val lastDateModified: LocalDateTime = LocalDateTime.MIN,
) : ObservablePlaylist {
    override val nameProperty = SimpleStringProperty()
    override val isDirectoryProperty = SimpleBooleanProperty()
    override val audioItemsProperty = SimpleListProperty<ObservableAudioItem>()
    override val audioItemsRecursiveProperty = SimpleListProperty<ObservableAudioItem>()
    override val playlistsProperty = SimpleSetProperty<ObservablePlaylist>()
    override val coverImageProperty = SimpleObjectProperty<Optional<Image>>()

    override fun addAudioItems(audioItems: Collection<ObservableAudioItem>): Boolean = throw IllegalStateException()

    override fun removeAudioItems(audioItems: Collection<ObservableAudioItem>): Boolean = throw IllegalStateException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIds")
    override fun removeAudioItems(audioItemIds: Collection<Int>): Boolean = throw IllegalStateException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    override fun removePlaylists(playlistIds: Collection<Int>): Boolean = throw IllegalStateException()

    override fun clearAudioItems() = throw IllegalStateException()

    override fun clearPlaylists() = throw IllegalStateException()

    override fun subscribe(p0: Flow.Subscriber<in EntityChangeEvent<Int, ObservablePlaylist>>?) = throw IllegalStateException()

    override fun removePlaylists(playlists: Collection<ObservablePlaylist>): Boolean = throw IllegalStateException()

    override fun addPlaylists(playlists: Collection<ObservablePlaylist>): Boolean = throw IllegalStateException()

    override fun clone(): DummyPlaylist = DummyPlaylist(id)
}
