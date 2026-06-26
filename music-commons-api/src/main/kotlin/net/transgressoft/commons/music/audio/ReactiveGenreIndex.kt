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
 * Represents a reactive index of all audio items for a specific genre.
 *
 * Each instance holds all audio items associated with a single [Genre] key, ordered by
 * artist name, album name, then track number. Because an audio item may belong to multiple
 * genres simultaneously, a single item can appear in multiple genre indexes at the same time.
 * This interface extends [ReactiveEntity] to support reactive updates when the contents change.
 *
 * Unlike the artist catalog, there is no sub-grouping within the index: every audio item tagged
 * with this genre is exposed directly via [tracks]. Items whose [ReactiveAudioItem.genres] set
 * is empty will not appear in any genre index.
 *
 * @param RGI The concrete type of this reactive genre index, for self-referential generics
 * @param I The type of audio items contained in this index
 */
interface ReactiveGenreIndex<RGI : ReactiveGenreIndex<RGI, I>, I : ReactiveAudioItem<I>> : ReactiveEntity<Genre, RGI> {

    /**
     * The genre this index represents.
     */
    val genre: Genre

    /**
     * The ordered tracks in this genre index, sorted by artist name, album name, then track number.
     */
    val tracks: List<I>

    /**
     * The total number of tracks in this genre index.
     */
    val size: Int

    /**
     * Indicates whether this genre index contains any tracks.
     */
    val isEmpty: Boolean
}