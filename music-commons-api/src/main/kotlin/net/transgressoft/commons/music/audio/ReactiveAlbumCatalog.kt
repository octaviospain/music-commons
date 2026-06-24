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
 * Represents a reactive flat catalog of all audio items for a specific album.
 *
 * Each catalog instance holds all audio items associated with a single [Album] key,
 * without sub-grouping by artist or any other axis. This interface extends [ReactiveEntity]
 * to support reactive updates when the catalog contents change, enabling consumers to
 * subscribe to changes in an album's item collection.
 *
 * Unlike the artist catalog, there is no album-level sub-grouping: every audio item
 * belonging to this album is exposed directly via [audioItems].
 *
 * @param ALC The concrete type of this album catalog, for self-referential generics
 * @param I The type of audio items contained in this catalog
 */
interface ReactiveAlbumCatalog<ALC : ReactiveAlbumCatalog<ALC, I>, I : ReactiveAudioItem<I>> : ReactiveEntity<Album, ALC> {

    /**
     * The album this catalog represents.
     */
    val album: Album

    /**
     * All audio items in this catalog, in natural comparable order, de-duplicated.
     */
    val audioItems: Set<I>

    /**
     * The total number of audio items in this catalog.
     */
    val size: Int

    /**
     * Indicates whether this catalog contains any audio items.
     */
    val isEmpty: Boolean

    /**
     * The name of the album this catalog represents.
     */
    val albumName: String get() = album.name

    /**
     * The primary artist credited for the album.
     */
    val albumArtist: Artist get() = album.albumArtist

    /**
     * Whether this album is a compilation of tracks from multiple artists.
     */
    val isCompilation: Boolean get() = album.isCompilation

    /**
     * The release year of the album, or `null` if not set.
     */
    val year: Short? get() = album.year

    /**
     * The record label associated with the album.
     */
    val label: Label get() = album.label

    /**
     * Cover image bytes resolved lazily from the first audio item in this catalog that carries a cover.
     *
     * The bytes are cached softly so they can be reclaimed under memory pressure and re-resolved on
     * next access. Resolution reuses each item's own on-demand cover loader — no per-track cover
     * data is retained beyond the one representative item. Returns `null` if no item in this catalog
     * carries cover data.
     */
    val coverImageBytes: ByteArray?
        get() = null
}