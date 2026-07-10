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

package net.transgressoft.commons.persistence.music.playlist

import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.persistence.music.lirpSerializerFor
import net.transgressoft.commons.persistence.music.rawConstruct
import net.transgressoft.lirp.entity.ReactiveEntityBase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * [KSerializer] for `Map<Int, MutableAudioPlaylist>` used to round-trip a playlist hierarchy
 * through JSON.
 *
 * Consumers wiring a custom `JsonFileRepository` pass this serializer directly:
 *
 * ```
 * val repository = JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer)
 * MusicLibrary.builder().playlistRepository(repository).build()
 * ```
 *
 * The element serializer is lirp's reflective `lirpSerializer(sample)`, which encodes the playlist's
 * identity, name, folder flag, and child references. The playlist's serialized fields are scalar
 * (`Int`/`String`/`Boolean`) plus aggregate-id lists, so no contextual serializers module is
 * required. The sample instance is built construction-free through the playlist's co-located
 * `_LirpRawConstructor` (resolved reflectively) — no internal constructor exposure, no factory.
 *
 * Thread-safety: the serializer is stateless; concurrent reads are safe.
 * @since 1.0
 */
@get:JvmName("AudioPlaylistMapSerializer")
public val AudioPlaylistMapSerializer: KSerializer<Map<Int, MutableAudioPlaylist>> =
    MapSerializer(Int.serializer(), lirpSerializerFor(samplePlaylist()))

private fun samplePlaylist(): ReactiveEntityBase<Int, MutableAudioPlaylist> =
    rawConstruct(
        "net.transgressoft.commons.music.playlist.MutablePlaylist",
        mapOf(
            "id" to 0,
            "name" to "",
            "isDirectory" to false
        )
    )