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
import java.util.TreeSet

/**
 * Immutable catalog of audio items for a single genre, built once from a list snapshot.
 *
 * All audio items tagged with this genre are held in a flat sorted, de-duplicated [Set],
 * ordered by their natural [Comparable] order. The set is built in the constructor and
 * never mutated afterwards, so all reads are lock-free.
 *
 * Because an audio item may belong to multiple genres simultaneously, the same item can
 * appear in multiple genre catalog instances. Items whose [ReactiveAudioItem.genres] set
 * is empty will not appear in any genre catalog.
 *
 * Duplicate detection uses item identity: if both items have an assigned `id`, they are
 * considered the same when their `id` values match; otherwise `uniqueId` is used. Ordering
 * follows the natural `compareTo` order of the audio item type, with ties broken by item
 * identity so that two distinct items whose `compareTo` returns 0 are both retained.
 *
 * @param I The type of audio items contained in this catalog
 */
internal class ImmutableGenreCatalog<I>(override val genre: Genre, audioItems: List<I>) :
    GenreCatalog<I>,
    ReactiveGenreCatalog<GenreCatalog<I>, I>,
    Comparable<GenreCatalog<I>>,
    ReactiveEntityBase<Genre, GenreCatalog<I>>()
    where I : ReactiveAudioItem<I> {

    private val logger = KotlinLogging.logger {}

    // Built once from the snapshot; sorted and de-duplicated, so reads need no lock.
    override val audioItems: Set<I> = buildFlatSet(audioItems)

    override val id: Genre = genre

    override val uniqueId: String = genre.name

    override val isEmpty: Boolean
        get() = audioItems.isEmpty()

    override val size: Int
        get() = audioItems.size

    override fun compareTo(other: GenreCatalog<I>): Int = this.genre.compareTo(other.genre)

    override fun clone(): ImmutableGenreCatalog<I> = this

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImmutableGenreCatalog<*>
        if (genre != other.genre) return false
        return audioItems == other.audioItems
    }

    override fun hashCode(): Int = 31 * genre.hashCode() + audioItems.hashCode()

    override fun toString() = "ImmutableGenreCatalog(genre=$genre, size=$size)"

    init {
        logger.debug { "Genre catalog created for ${genre.name}" }
    }

    companion object {

        private fun <I> buildFlatSet(audioItems: List<I>): Set<I> where I : ReactiveAudioItem<I> {
            // Insertion-sort into a de-duplicated sorted set; all items for one genre
            // land in a single flat bucket without sub-grouping.
            // Primary order is artist → album → disc/track; the identity tie-break ensures
            // that two distinct items with the same sort key are both retained in the set.
            val result =
                TreeSet<I>(audioItemArtistAlbumTrackComparator<I>().thenComparing(audioItemIdentityComparator()))
            for (audioItem in audioItems) {
                if (result.none { isSameAudioItem(it, audioItem) }) {
                    result.add(audioItem)
                }
            }
            return result
        }

        private fun <I> isSameAudioItem(left: I, right: I): Boolean
                where I : ReactiveAudioItem<I> =
            if (left.id != UNASSIGNED_ID && right.id != UNASSIGNED_ID) {
                left.id == right.id
            } else {
                left === right || left.uniqueId == right.uniqueId
            }
    }
}