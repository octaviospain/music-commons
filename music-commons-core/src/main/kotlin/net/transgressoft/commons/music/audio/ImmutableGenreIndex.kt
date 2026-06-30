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

import net.transgressoft.lirp.entity.ReactiveEntityBase
import mu.KotlinLogging

/**
 * Immutable genre index holding an ordered list of audio items for a single [Genre] key.
 *
 * Items are delivered pre-sorted by the projection's [entryOrdering] comparator (artist then album
 * then disc/track). The list is stored verbatim — no re-sorting or deduplication happens here
 * because the lirp projection guarantees both ordering and distinctness via its internal reverse
 * index.
 *
 * Because an audio item may belong to multiple genres simultaneously, the same item can appear in
 * multiple genre index instances. Items whose [ReactiveAudioItem.genres] set is empty surface in the
 * dedicated [Genre.None] index rather than being dropped.
 *
 * @param I The type of audio items contained in this index
 */
internal class ImmutableGenreIndex<I>(override val genre: Genre, tracks: List<I>) :
    GenreIndex<I>,
    ReactiveGenreIndex<GenreIndex<I>, I>,
    Comparable<GenreIndex<I>>,
    ReactiveEntityBase<Genre, GenreIndex<I>>()
    where I : ReactiveAudioItem<I> {

    private val logger = KotlinLogging.logger {}

    // Defensive copy; lirp delivers the list pre-sorted so no re-sorting is needed.
    override val tracks: List<I> = tracks.toList()

    override val id: Genre = genre

    override val uniqueId: String = genre.name

    override val isEmpty: Boolean
        get() = tracks.isEmpty()

    override val size: Int
        get() = tracks.size

    override fun compareTo(other: GenreIndex<I>): Int = this.genre.compareTo(other.genre)

    override fun clone(): ImmutableGenreIndex<I> = this

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImmutableGenreIndex<*>
        if (genre != other.genre) return false
        return tracks == other.tracks
    }

    override fun hashCode(): Int = 31 * genre.hashCode() + tracks.hashCode()

    override fun toString() = "ImmutableGenreIndex(genre=$genre, size=$size)"

    init {
        logger.debug { "Genre index created for ${genre.name}" }
    }
}