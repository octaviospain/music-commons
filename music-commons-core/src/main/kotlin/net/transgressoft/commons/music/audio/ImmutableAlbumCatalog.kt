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
import java.lang.ref.SoftReference
import java.util.TreeSet

/**
 * Immutable catalog of audio items for a single album, built once from a list snapshot.
 *
 * All audio items for the album are held in a flat sorted, de-duplicated [Set], ordered
 * by their natural [Comparable] order. The set is built in the constructor and never
 * mutated afterwards, so all reads are lock-free.
 *
 * Duplicate detection uses item identity: if both items have an assigned `id`, they are
 * considered the same when their `id` values match; otherwise `uniqueId` is used. Ordering
 * follows the natural `compareTo` order of the audio item type, with ties broken by item
 * identity so that two distinct items whose `compareTo` returns 0 are both retained.
 *
 * @param I The type of audio items contained in this catalog
 */
internal class ImmutableAlbumCatalog<I>(override val album: Album, audioItems: List<I>) :
    AlbumCatalog<I>,
    ReactiveAlbumCatalog<AlbumCatalog<I>, I>,
    Comparable<AlbumCatalog<I>>,
    ReactiveEntityBase<Album, AlbumCatalog<I>>()
    where I : ReactiveAudioItem<I> {

    private val logger = KotlinLogging.logger {}

    // Built once from the snapshot; sorted and de-duplicated, so reads need no lock.
    override val audioItems: Set<I> = buildFlatSet(audioItems)

    override val id: Album = album

    override val uniqueId: String = album.name

    override val isEmpty: Boolean
        get() = audioItems.isEmpty()

    override val size: Int
        get() = audioItems.size

    @Volatile
    private var coverRef: SoftReference<ByteArray>? = null

    @Volatile
    private var noCover: Boolean = false

    override val coverImageBytes: ByteArray?
        get() {
            coverRef?.get()?.let { return it }
            if (noCover) return null
            synchronized(this) {
                coverRef?.get()?.let { return it }
                if (noCover) return null
                val bytes = firstCoverImageBytes(audioItems)
                return if (bytes != null) {
                    coverRef = SoftReference(bytes)
                    bytes
                } else {
                    noCover = true
                    null
                }
            }
        }

    override fun compareTo(other: AlbumCatalog<I>): Int = this.album.compareTo(other.album)

    override fun clone(): ImmutableAlbumCatalog<I> = this

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImmutableAlbumCatalog<*>
        if (album != other.album) return false
        return audioItems == other.audioItems
    }

    override fun hashCode(): Int = 31 * album.hashCode() + audioItems.hashCode()

    override fun toString() = "ImmutableAlbumCatalog(album=$album, size=$size)"

    init {
        logger.debug { "Album catalog created for ${album.name}" }
    }

    companion object {

        private fun <I> buildFlatSet(audioItems: List<I>): Set<I> where I : ReactiveAudioItem<I> {
            // Insertion-sort into a de-duplicated sorted set; all items for one album
            // land in a single flat bucket without sub-grouping.
            // Primary order is disc/track; the identity tie-break ensures that two distinct
            // items with the same disc and track number are both retained in the set.
            val result =
                TreeSet<I>(audioItemTrackDiscNumberComparator<I>().thenComparing(audioItemIdentityComparator()))
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