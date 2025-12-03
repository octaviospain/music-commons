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

import net.transgressoft.commons.event.MutationEvent
import net.transgressoft.commons.event.TransEventPublisher
import net.transgressoft.commons.event.TransEventSubscription
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.playlist.AudioPlaylistSerializerBase
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleSetProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.image.Image
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@get:JvmName("ObservablePlaylistMapSerializer")
val ObservablePlaylistMapSerializer = MapSerializer(Int.serializer(), ObservablePlaylistSerializer)

/**
 * Kotlinx serialization serializer for [ObservablePlaylist] instances.
 *
 * Serializes JavaFX playlists to JSON by storing audio item and nested playlist IDs.
 * Creates dummy placeholder instances during deserialization that are later resolved
 * to actual playlist instances by [ObservablePlaylistHierarchy] during initialization.
 */
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
    override val lastDateModified: LocalDateTime = LocalDateTime.MIN
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

    override fun subscribe(p0: Flow.Subscriber<in MutationEvent<Int, ObservablePlaylist>>?) = throw IllegalStateException()

    override fun removePlaylists(playlists: Collection<ObservablePlaylist>): Boolean = throw IllegalStateException()

    override fun addPlaylists(playlists: Collection<ObservablePlaylist>): Boolean = throw IllegalStateException()

    override fun clone(): DummyPlaylist = DummyPlaylist(id)

    override val changes: SharedFlow<MutationEvent<Int, ObservablePlaylist>>
        get() = throw UnsupportedOperationException()

    override fun emitAsync(event: MutationEvent<Int, ObservablePlaylist>): Unit = throw UnsupportedOperationException()

    override fun subscribe(action: suspend (MutationEvent<Int, ObservablePlaylist>) -> Unit):
        TransEventSubscription<in ObservablePlaylist, MutationEvent.Type, MutationEvent<Int, ObservablePlaylist>> =
        FakeSubscription

    override fun subscribe(action: Consumer<in MutationEvent<Int, ObservablePlaylist>>):
        TransEventSubscription<in ObservablePlaylist, MutationEvent.Type, MutationEvent<Int, ObservablePlaylist>> =
        FakeSubscription

    override fun subscribe(vararg eventTypes: MutationEvent.Type, action: Consumer<in MutationEvent<Int, ObservablePlaylist>>):
        TransEventSubscription<in ObservablePlaylist, MutationEvent.Type, MutationEvent<Int, ObservablePlaylist>> =
        FakeSubscription
}

object FakeSubscription : TransEventSubscription<ObservablePlaylist, MutationEvent.Type, MutationEvent<Int, ObservablePlaylist>> {
    override val source: TransEventPublisher<MutationEvent.Type, MutationEvent<Int, ObservablePlaylist>>
        get() = throw IllegalStateException()

    override fun request(n: Long): Unit = throw IllegalStateException()

    override fun cancel() {
        // No-op
    }
}