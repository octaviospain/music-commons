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

import net.transgressoft.lirp.entity.ReactiveEntity

/**
 * Represents a reactive catalog of all audio items for a specific artist.
 *
 * A catalog organizes an artist's audio items by album and provides efficient access
 * to the artist's discography. This interface extends [ReactiveEntity] to support
 * reactive updates when the catalog contents change, enabling consumers to subscribe
 * to changes in an artist's collection.
 *
 * @param AC The concrete type of this artist catalog, allowing for self-referential
 *           generic types in reactive operations
 * @param I The type of audio items contained in this catalog
 * @since 1.0
 */
public interface ReactiveArtistCatalog<AC : ReactiveArtistCatalog<AC, I>, I : ReactiveAudioItem<I>> : ReactiveEntity<Artist, AC> {

    /**
     * The artist this catalog represents.
     * @since 1.0
     */
    public val artist: Artist

    /**
     * The display name of the artist this catalog represents.
     * @since 1.0
     */
    public val artistName: String get() = artist.name

    /**
     * All albums in this artist's catalog, each containing its tracks.
     * @since 1.0
     */
    public val albums: Set<ReactiveAlbum<*, I>>

    /**
     * The total number of audio items in this catalog across all albums.
     * @since 1.0
     */
    public val size: Int

    /**
     * Retrieves all audio items for the specified album.
     *
     * @param album The album details to retrieve items for
     * @return Set of audio items belonging to the album, or empty set if album not found
     * @since 1.0
     */
    public fun albumAudioItems(album: AlbumDetails): Set<I> = albumAudioItems(album.name)

    /**
     * Retrieves all audio items for the album with the specified name.
     *
     * @param albumName The name of the album to retrieve items for
     * @return Set of audio items belonging to the album, or empty set if album not found
     * @since 1.0
     */
    public fun albumAudioItems(albumName: String): Set<I>

    /**
     * Indicates whether this catalog contains any audio items.
     * @since 1.0
     */
    public val isEmpty: Boolean
}