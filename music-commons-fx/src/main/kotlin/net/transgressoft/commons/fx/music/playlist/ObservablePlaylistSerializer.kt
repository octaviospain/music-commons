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

import net.transgressoft.lirp.persistence.json.lirpSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * [KSerializer] for `Map<Int, ObservablePlaylist>` — the JavaFX-typed counterpart of
 * `AudioPlaylistMapSerializer` for use with the FX `MusicLibrary`.
 *
 * Consumers wiring a custom `JsonFileRepository` pass this serializer directly:
 *
 * ```
 * val repository = JsonFileRepository(playlistsFile, ObservablePlaylistMapSerializer)
 * FXMusicLibrary.builder().playlistRepository(repository).build()
 * ```
 *
 * The element serializer is produced by `lirpSerializer(FXPlaylist(...))`, encoding the
 * playlist identity, name, folder flag, and child references. JavaFX observable properties
 * are reconstructed during deserialization. Polymorphic resolution of the concrete `FXPlaylist`
 * subtype is wired by the lirp framework — no additional `serializersModule` plumbing is
 * required at consumer call sites.
 *
 * Thread-safety: the serializer is stateless; concurrent reads are safe.
 */
@get:JvmName("ObservablePlaylistMapSerializer")
@Suppress("UNCHECKED_CAST") // Safe cast: generic type erased at runtime but guaranteed by the builder/serializer contract
val ObservablePlaylistMapSerializer: KSerializer<Map<Int, ObservablePlaylist>> =
    MapSerializer(Int.serializer(), lirpSerializer(FXPlaylist(0, "", false))) as KSerializer<Map<Int, ObservablePlaylist>>