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

/**
 * Represents the album attributes of an audio item's album
 */
interface Album : Comparable<Album> {
    val name: String
    val albumArtist: Artist
    val isCompilation: Boolean
    val year: Short?
    val label: Label
}

/**
 * Represents an album as a list of audio items.
 *
 * This interface extends [List] to provide convenient access to all audio items
 * on an album while also exposing the album's name. Instances are typically
 * immutable snapshots of an album's contents at a point in time.
 *
 * @param I The type of audio items in this album set
 */
interface AlbumSet<I : ReactiveAudioItem<I>> : List<I>, Comparable<AlbumSet<I>> {
    /**
     * The name of the album.
     */
    val albumName: String
}