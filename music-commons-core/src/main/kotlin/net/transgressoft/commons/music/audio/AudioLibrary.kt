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

package net.transgressoft.commons.music.audio

import java.util.Optional

/**
 * Narrowed audio library interface for [AudioItem], [ArtistCatalog], [Album], and [GenreIndex] types.
 *
 * Provides a clean, non-generic entry point for consumers that work with the core domain types.
 * Extends [ReactiveAudioLibrary] with concrete type parameters, removing the need to specify
 * generics at call sites.
 */
interface AudioLibrary :
    ReactiveAudioLibrary<AudioItem, ArtistCatalog<AudioItem>, Album<AudioItem>, GenreIndex<AudioItem>>

/**
 * Resolves the album this item belongs to from the given library.
 *
 * Call-site sugar over `library.getAlbum(item.album)` — the item holds no library back-reference,
 * so navigation to its populated album always requires passing the library explicitly.
 *
 * @param library The library to look up the album in
 * @return An [Optional] containing the album if it is present in the library, or empty if not found
 */
fun AudioItem.albumIn(library: AudioLibrary): Optional<out ReactiveAlbum<*, AudioItem>> = library.getAlbum(this.album)