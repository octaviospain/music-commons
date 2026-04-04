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
import net.transgressoft.lirp.persistence.mutableAggregateList
import net.transgressoft.lirp.persistence.mutableAggregateSet

/**
 * Concrete implementation of [MutableAudioPlaylist] as a top-level class.
 *
 * Audio item and nested playlist references are resolved lazily via lirp mutable aggregate
 * delegates backed by [audioItems] and [playlists] respectively. Mutations through the
 * aggregate proxies update the internal backing ID collections and trigger mutation events.
 *
 * The manual [MutablePlaylist_LirpRefAccessor] registers the aggregate delegates with lirp so
 * that registry binding and mutation event wiring work correctly at runtime.
 *
 * Registered in LirpContext via [net.transgressoft.lirp.persistence.RegistryBase.registerRepository]
 * by [DefaultPlaylistHierarchy] on construction.
 */
internal class MutablePlaylist(
    id: Int,
    name: String,
    isDirectory: Boolean,
    initialAudioItemIds: List<Int> = emptyList(),
    initialPlaylistIds: Set<Int> = emptySet()
) : MutablePlaylistBase<AudioItem, MutableAudioPlaylist>(id, name, isDirectory),
    MutableAudioPlaylist {

    // @Aggregate is intentionally omitted here — a manual MutablePlaylist_LirpRefAccessor is
    // provided instead, because KSP generates a public accessor that cannot reference internal classes.
    override val audioItems by mutableAggregateList<Int, AudioItem>(initialAudioItemIds)

    override val playlists by mutableAggregateSet<Int, MutableAudioPlaylist>(initialPlaylistIds)

    override fun clone(): MutablePlaylist =
        MutablePlaylist(id, name, isDirectory, audioItems.referenceIds.toList(), LinkedHashSet(playlists.referenceIds))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MutablePlaylist) return false
        return id == other.id &&
            name == other.name &&
            isDirectory == other.isDirectory &&
            audioItems.referenceIds == other.audioItems.referenceIds &&
            playlists.referenceIds == other.playlists.referenceIds
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + isDirectory.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + audioItems.referenceIds.hashCode()
        result = 31 * result + playlists.referenceIds.hashCode()
        return result
    }
}