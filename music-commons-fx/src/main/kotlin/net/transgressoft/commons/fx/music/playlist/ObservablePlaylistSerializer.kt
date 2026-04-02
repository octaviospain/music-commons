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
import net.transgressoft.commons.music.playlist.AudioPlaylistSerializerBase
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.event.LirpEventSubscription
import net.transgressoft.lirp.event.MutationEvent
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.ReadOnlyStringProperty
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@get:JvmName("ObservablePlaylistMapSerializer")
val ObservablePlaylistMapSerializer = MapSerializer(Int.serializer(), ObservablePlaylistSerializer)

/**
 * Kotlinx serialization serializer for [ObservablePlaylist] instances.
 *
 * Serializes JavaFX playlists to JSON by storing audio item and nested playlist IDs.
 * Creates [ImmutableObservablePlaylist] placeholder instances during deserialization that are later
 * replaced by real [ObservablePlaylist] instances during [ObservablePlaylistHierarchy] initialization.
 * Audio item references are resolved from [net.transgressoft.lirp.persistence.LirpContext] once the
 * audio library has registered its repository.
 */
object ObservablePlaylistSerializer : AudioPlaylistSerializerBase<ObservableAudioItem, ObservablePlaylist>() {
    @Suppress("UNCHECKED_CAST")
    override fun createInstance(propertiesList: List<Any?>): ObservablePlaylist =
        ImmutableObservablePlaylist(
            id = propertiesList[0] as Int,
            isDirectory = propertiesList[1] as Boolean,
            name = propertiesList[2] as String,
            audioItemIds = propertiesList[3] as List<Int>,
            playlistIds = propertiesList[4] as Set<Int>
        )

    override fun getAudioItemIds(value: ObservablePlaylist): List<Int> =
        if (value is ImmutableObservablePlaylist) value.audioItemIds else value.audioItems.map { it.id }

    override fun getPlaylistIds(value: ObservablePlaylist): List<Int> =
        if (value is ImmutableObservablePlaylist) value.playlistIds.toList() else value.playlists.map { it.id }
}

/**
 * Immutable stub implementation of [ObservablePlaylist] used as a transient deserialization placeholder.
 *
 * Created by [ObservablePlaylistSerializer] when reading JSON. The [audioItemIds] and [playlistIds]
 * fields carry referenced entity IDs that [ObservablePlaylistHierarchy] uses to resolve real
 * [ObservablePlaylist] instances via [net.transgressoft.lirp.persistence.LirpContext].
 * All reactive and JavaFX operations are no-ops since this stub is replaced during initialization.
 */
internal class ImmutableObservablePlaylist(
    override val id: Int,
    override var isDirectory: Boolean = false,
    override var name: String = "",
    override val audioItems: List<ObservableAudioItem> = emptyList(),
    override val playlists: Set<ObservablePlaylist> = emptySet(),
    override val lastDateModified: LocalDateTime = LocalDateTime.MIN,
    val audioItemIds: List<Int> = audioItems.map { it.id },
    val playlistIds: Set<Int> = playlists.map { it.id }.toSet()
) : ObservablePlaylist {
    override val nameProperty: ReadOnlyStringProperty = SimpleStringProperty()
    override val isDirectoryProperty: ReadOnlyBooleanProperty = SimpleBooleanProperty()
    override val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> = SimpleListProperty()
    override val audioItemsRecursiveProperty: ReadOnlyListProperty<ObservableAudioItem> = SimpleListProperty()
    override val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist> = SimpleSetProperty()
    override val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>> = SimpleObjectProperty()

    private val _changes = MutableSharedFlow<MutationEvent<Int, ObservablePlaylist>>(extraBufferCapacity = 1)
    override val changes: SharedFlow<MutationEvent<Int, ObservablePlaylist>> = _changes.asSharedFlow()

    override val isClosed: Boolean = false

    override fun addAudioItems(audioItems: Collection<ObservableAudioItem>): Boolean = false

    override fun removeAudioItems(audioItems: Collection<ObservableAudioItem>): Boolean = false

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIds")
    override fun removeAudioItems(audioItemIds: Collection<Int>): Boolean = false

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    override fun removePlaylists(playlistIds: Collection<Int>): Boolean = false

    override fun clearAudioItems() {}

    override fun clearPlaylists() {}

    override fun subscribe(p0: Flow.Subscriber<in MutationEvent<Int, ObservablePlaylist>>?) {}

    override fun removePlaylists(playlists: Collection<ObservablePlaylist>): Boolean = false

    override fun addPlaylists(playlists: Collection<ObservablePlaylist>): Boolean = false

    override fun close() {}

    override fun clone(): ImmutableObservablePlaylist =
        ImmutableObservablePlaylist(id, isDirectory, name, audioItems, playlists, lastDateModified, audioItemIds, playlistIds)

    override fun emitAsync(event: MutationEvent<Int, ObservablePlaylist>) {}

    override fun subscribe(action: suspend (MutationEvent<Int, ObservablePlaylist>) -> Unit):
        LirpEventSubscription<in ObservablePlaylist, MutationEvent.Type, MutationEvent<Int, ObservablePlaylist>> =
        NoOpObservablePlaylistSubscription

    override fun subscribe(action: Consumer<in MutationEvent<Int, ObservablePlaylist>>):
        LirpEventSubscription<in ObservablePlaylist, MutationEvent.Type, MutationEvent<Int, ObservablePlaylist>> =
        NoOpObservablePlaylistSubscription

    override fun subscribe(vararg eventTypes: MutationEvent.Type, action: Consumer<in MutationEvent<Int, ObservablePlaylist>>):
        LirpEventSubscription<in ObservablePlaylist, MutationEvent.Type, MutationEvent<Int, ObservablePlaylist>> =
        NoOpObservablePlaylistSubscription
}

private object NoOpObservablePlaylistSubscription :
    LirpEventSubscription<ObservablePlaylist, MutationEvent.Type, MutationEvent<Int, ObservablePlaylist>> {
    override val source: LirpEventPublisher<MutationEvent.Type, MutationEvent<Int, ObservablePlaylist>>
        get() = throw UnsupportedOperationException("NoOpObservablePlaylistSubscription has no source publisher")

    override fun request(n: Long) {}

    override fun cancel() {}
}