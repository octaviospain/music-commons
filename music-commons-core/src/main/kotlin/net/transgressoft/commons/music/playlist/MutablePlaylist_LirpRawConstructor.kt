@file:Suppress("ktlint:standard:filename")

/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

import net.transgressoft.lirp.persistence.LirpRawConstructor

/**
 * Co-located construction SPI for [MutablePlaylist].
 *
 * Resolved at runtime by `SqlRepository.loadFromStore` (via a `RawConstructibleTableDef`) through
 * `Class.forName` on the entity's binary name plus the `_LirpRawConstructor` suffix. Living in the
 * entity's own module, it reaches the `internal` constructor a persistence module cannot call. The
 * scalar reactive fields are re-applied afterward by [MutablePlaylist_LirpRawInitializer].
 *
 * Aggregate references are restored at construction time: the `mutableAggregateList` and
 * `mutableAggregateSet` delegates capture their backing IDs from the constructor arguments, so the
 * persistence side passes the audio-item and child-playlist IDs here rather than relying on a
 * post-construction setter. When the keys are absent (the JSON path supplies only identity/scalar
 * fields), the collections default to empty.
 *
 * The [construct] `params` map is keyed by constructor parameter name and produced by the
 * persistence-side `RawConstructibleTableDef.constructorParams`. Expected keys: `id` ([Int]),
 * `name` ([String]), `isDirectory` ([Boolean]). Optional keys: `initialAudioItemIds`
 * ([List]<[Int]>), `initialPlaylistIds` ([Set]<[Int]>).
 */
@Suppress("ClassName", "UNCHECKED_CAST")
internal class MutablePlaylist_LirpRawConstructor : LirpRawConstructor<MutablePlaylist> {
    override fun construct(params: Map<String, Any?>): MutablePlaylist =
        MutablePlaylist(
            id = params["id"] as Int,
            name = params["name"] as String,
            isDirectory = params["isDirectory"] as Boolean,
            initialAudioItemIds = (params["initialAudioItemIds"] as? List<Int>) ?: emptyList(),
            initialPlaylistIds = (params["initialPlaylistIds"] as? Set<Int>) ?: emptySet()
        )
}