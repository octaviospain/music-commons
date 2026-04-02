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

package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.event.LirpEventSubscription
import net.transgressoft.lirp.event.MutationEvent
import java.time.LocalDateTime
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Immutable snapshot implementation of [MutableAudioPlaylist].
 *
 * Used as a transient deserialization stub between JSON parsing and [DefaultPlaylistHierarchy]
 * initialization. The [audioItemIds] and [playlistIds] fields carry referenced entity IDs that
 * the hierarchy init block uses to resolve real [MutableAudioPlaylist] instances via lirp's
 * aggregate delegate mechanism.
 *
 * All reactive operations inherited from [MutableAudioPlaylist] are no-ops or unsupported since
 * this stub is replaced by real playlist instances during [DefaultPlaylistHierarchy] construction.
 */
internal class ImmutablePlaylist(
    override var id: Int = UNASSIGNED_ID,
    override var isDirectory: Boolean,
    override var name: String,
    override val audioItems: List<AudioItem> = emptyList(),
    override val playlists: Set<MutableAudioPlaylist> = emptySet(),
    val audioItemIds: List<Int> = audioItems.map { it.id },
    val playlistIds: Set<Int> = playlists.map { it.id }.toSet()
) : MutableAudioPlaylist {

    private val _changes = MutableSharedFlow<MutationEvent<Int, MutableAudioPlaylist>>(extraBufferCapacity = 1)

    override val lastDateModified: LocalDateTime = LocalDateTime.MIN
    override val isClosed: Boolean = false
    override val changes: SharedFlow<MutationEvent<Int, MutableAudioPlaylist>> = _changes.asSharedFlow()

    override fun addAudioItems(audioItems: Collection<AudioItem>): Boolean = false

    override fun removeAudioItems(audioItems: Collection<AudioItem>): Boolean = false

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIds")
    override fun removeAudioItems(audioItemIds: Collection<Int>): Boolean = false

    override fun addPlaylists(playlists: Collection<MutableAudioPlaylist>): Boolean = false

    override fun removePlaylists(playlists: Collection<MutableAudioPlaylist>): Boolean = false

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    override fun removePlaylists(playlistIds: Collection<Int>): Boolean = false

    override fun clearAudioItems() {}

    override fun clearPlaylists() {}

    override fun emitAsync(event: MutationEvent<Int, MutableAudioPlaylist>) {}

    override fun subscribe(p0: Flow.Subscriber<in MutationEvent<Int, MutableAudioPlaylist>>?) {}

    override fun subscribe(action: suspend (MutationEvent<Int, MutableAudioPlaylist>) -> Unit):
        LirpEventSubscription<in MutableAudioPlaylist, MutationEvent.Type, MutationEvent<Int, MutableAudioPlaylist>> =
        NoOpSubscription

    override fun subscribe(action: Consumer<in MutationEvent<Int, MutableAudioPlaylist>>):
        LirpEventSubscription<in MutableAudioPlaylist, MutationEvent.Type, MutationEvent<Int, MutableAudioPlaylist>> =
        NoOpSubscription

    override fun subscribe(
        vararg eventTypes: MutationEvent.Type,
        action: Consumer<in MutationEvent<Int, MutableAudioPlaylist>>
    ): LirpEventSubscription<in MutableAudioPlaylist, MutationEvent.Type, MutationEvent<Int, MutableAudioPlaylist>> =
        NoOpSubscription

    override fun close() {}

    override fun clone(): ImmutablePlaylist =
        ImmutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet(), audioItemIds.toList(), playlistIds.toSet())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutablePlaylist

        if (isDirectory != other.isDirectory) return false
        if (name != other.name) return false
        if (audioItems != other.audioItems) return false
        if (playlists != other.playlists) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDirectory.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + audioItems.hashCode()
        result = 31 * result + playlists.hashCode()
        return result
    }

    override fun toString() = "ImmutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
}

private object NoOpSubscription : LirpEventSubscription<MutableAudioPlaylist, MutationEvent.Type, MutationEvent<Int, MutableAudioPlaylist>> {
    override val source: LirpEventPublisher<MutationEvent.Type, MutationEvent<Int, MutableAudioPlaylist>>
        get() = throw UnsupportedOperationException("NoOpSubscription has no source publisher")

    override fun request(n: Long) {}

    override fun cancel() {}
}