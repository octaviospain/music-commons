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

/**
 * Narrowed playlist hierarchy interface for [AudioItem] and [MutableAudioPlaylist] types.
 *
 * Provides a clean, non-generic entry point for consumers that work with the core domain types.
 * Extends [ReactivePlaylistHierarchy] with concrete type parameters, removing the need to specify
 * generics at call sites.
 */
interface PlaylistHierarchy : ReactivePlaylistHierarchy<AudioItem, MutableAudioPlaylist>