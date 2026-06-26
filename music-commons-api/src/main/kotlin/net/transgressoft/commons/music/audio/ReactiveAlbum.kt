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
 * Represents a reactive ordered collection of audio items for a specific album.
 *
 * Each instance holds all audio items associated with a single [AlbumDetails] key,
 * without sub-grouping by artist or any other axis. This interface extends [ReactiveEntity]
 * to support reactive updates when the contents change, enabling consumers to subscribe
 * to changes in an album's item collection.
 *
 * Unlike the artist catalog, there is no sub-grouping: every audio item belonging to this
 * album is exposed directly via [tracks], ordered by disc number then track number.
 *
 * @param RA The concrete type of this reactive album, for self-referential generics
 * @param I The type of audio items contained in this album
 */
interface ReactiveAlbum<RA : ReactiveAlbum<RA, I>, I : ReactiveAudioItem<I>> : ReactiveEntity<AlbumDetails, RA> {

    /**
     * The album details this instance represents.
     */
    val album: AlbumDetails

    /**
     * The ordered tracks in this album, sorted by disc number then track number.
     */
    val tracks: List<I>

    /**
     * The total number of tracks in this album.
     */
    val size: Int

    /**
     * Indicates whether this album contains any tracks.
     */
    val isEmpty: Boolean

    /**
     * The name of the album.
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
     * Cover image bytes resolved lazily from the first track in this album that carries a cover.
     *
     * The bytes are cached softly so they can be reclaimed under memory pressure and re-resolved on
     * next access. Resolution reuses each track's own on-demand cover loader and stops at the first
     * track that yields cover data — so every track probed up to and including that one may populate
     * its own cover cache as a side effect. Returns `null` if no track in this album carries cover data.
     */
    val coverImageBytes: ByteArray?
        get() = null
}