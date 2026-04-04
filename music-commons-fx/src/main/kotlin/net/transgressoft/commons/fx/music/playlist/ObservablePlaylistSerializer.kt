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

@get:JvmName("ObservablePlaylistMapSerializer")
@Suppress("UNCHECKED_CAST")
internal val ObservablePlaylistMapSerializer: KSerializer<Map<Int, ObservablePlaylist>> =
    MapSerializer(Int.serializer(), lirpSerializer(FXPlaylist(0, "", false))) as KSerializer<Map<Int, ObservablePlaylist>>