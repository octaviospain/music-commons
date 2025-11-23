/******************************************************************************
 *     Copyright (C) 2025  Octavio Calleya Garcia                             *
 *                                                                            *
 *     This program is free software: you can redistribute it and/or modify   *
 *     it under the terms of the GNU General Public License as published by   *
 *     the Free Software Foundation, either version 3 of the License, or      *
 *     (at your option) any later version.                                    *
 *                                                                            *
 *     This program is distributed in the hope that it will be useful,        *
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of         *
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the          *
 *     GNU General Public License for more details.                           *
 *                                                                            *
 *     You should have received a copy of the GNU General Public License      *
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>. *
 ******************************************************************************/

package net.transgressoft.commons.music.audio

/**
 * Represents an immutable view of an album containing its name and associated audio items.
 *
 * This class implements both [AlbumSet] and [List] to provide convenient access to an album's
 * audio items. The items are maintained in sorted order (by disc and track number), and all items
 * are guaranteed to belong to the same album.
 *
 * This is an internal implementation used by [MutableArtistCatalog] to expose album data
 * to consumers in a read-only manner. While the underlying catalog may change, this snapshot
 * remains immutable once created.
 *
 * @param I The type of audio items on this album view
 * @param albumName The name of the album
 * @param backing The list of audio items belonging to this album
 * @throws IllegalArgumentException if a backing list is empty, contains items from different albums or contains duplicate items
 */
internal class AlbumView<I : ReactiveAudioItem<I>>(
    override val albumName: String,
    private val backing: List<I>
) : AlbumSet<I>, List<I> by backing {

    init {
        require(backing.isNotEmpty()) { "Backing list must not be empty" }

        require(backing.all { it.album.name == albumName }) {
            "All audio items in the backing list must have album name '$albumName'"
        }

        require(backing.distinctBy { it }.size == backing.size) {
            "Backing list must not contain duplicate audio items"
        }
    }

    override fun compareTo(other: AlbumSet<I>): Int = albumName.compareTo(other.albumName)
}