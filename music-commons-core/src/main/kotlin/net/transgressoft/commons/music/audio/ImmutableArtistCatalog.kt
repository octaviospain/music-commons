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
 * Immutable catalog of audio items for a single artist, built once from a list snapshot.
 *
 * Organizes audio items by their full album identity ([AlbumDetails]) and maintains each album's
 * items sorted by disc and track number. The album-grouped structure is built in the constructor and
 * never mutated afterwards, so all reads are lock-free. Because albums are keyed by full value, two
 * albums sharing a name but differing in label, year, or album artist are distinct buckets.
 *
 * Duplicate detection uses item identity: if both items have an assigned `id`, they are
 * considered the same when their `id` values match; otherwise `uniqueId` is used. Ordering
 * within each album list follows the natural `compareTo` order of the audio item type.
 *
 * @param I The type of audio items contained in this catalog
 */
internal class ImmutableArtistCatalog<I>(override val artist: Artist, audioItems: List<I>) :
    ArtistCatalog<I>,
    ReactiveArtistCatalog<ArtistCatalog<I>, I>,
    Comparable<ArtistCatalog<I>>,
    ReactiveEntityBase<Artist, ArtistCatalog<I>>()
    where I : ReactiveAudioItem<I> {

    private val logger = KotlinLogging.logger {}

    // Built once from the snapshot; insertion-sorted and de-duplicated per full-value album, so reads need no lock.
    private val audioItemsByAlbum: Map<AlbumDetails, List<I>> = buildAlbumMap(audioItems)

    override val id: Artist = artist

    override val uniqueId: String = artist.id()

    // One bucket per full-value album, built once from the immutable snapshot. Consistent with the
    // album-axis identity model: albums sharing a name but differing in label/year/album artist are distinct.
    override val albums: Set<ReactiveAlbum<*, I>> =
        audioItemsByAlbum
            .mapNotNull { (albumDetails, items) ->
                if (items.isEmpty()) null else ImmutableAlbum(albumDetails, items)
            }.toSet()

    /**
     * Returns the union of items across every album whose name equals [albumName]. Because albums are
     * keyed by full [AlbumDetails], a single name may span more than one distinct album; this is a
     * name-level aggregate. To obtain a single album's items, iterate [albums] and read the bucket's
     * own track list rather than calling this with the album name.
     */
    override fun albumAudioItems(albumName: String): Set<I> =
        audioItemsByAlbum
            .asSequence()
            .filter { it.key.name == albumName }
            .flatMap { it.value.asSequence() }
            .toSet()

    override val isEmpty: Boolean
        get() = audioItemsByAlbum.isEmpty()

    override val size: Int
        get() = audioItemsByAlbum.values.sumOf { it.size }

    override fun compareTo(other: ArtistCatalog<I>): Int = this.artist.compareTo(other.artist)

    override fun clone(): ImmutableArtistCatalog<I> = this

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImmutableArtistCatalog<*>
        if (artist != other.artist) return false
        return audioItemsByAlbum == other.audioItemsByAlbum
    }

    override fun hashCode(): Int = 31 * artist.hashCode() + audioItemsByAlbum.hashCode()

    override fun toString() = "ImmutableArtistCatalog(artist=$artist, size=$size)"

    init {
        logger.debug { "Artist catalog created for ${artist.id()}" }
    }

    companion object {

        private fun <I> buildAlbumMap(audioItems: List<I>): Map<AlbumDetails, List<I>> where I : ReactiveAudioItem<I> {
            // Build sorted, de-duplicated per-album lists in one pass, keyed by full album identity.
            // Insertion sort preserves the natural compareTo order; isSameAudioItem guards against duplicates.
            val map = sortedMapOf<AlbumDetails, MutableList<I>>()
            for (audioItem in audioItems) {
                val bucket = map.getOrPut(audioItem.album) { mutableListOf() }
                if (bucket.none { isSameAudioItem(it, audioItem) }) {
                    val insertionPoint =
                        bucket
                            .indexOfFirst { it > audioItem }
                            .let {
                                if (it >= 0)
                                    it
                                else
                                    bucket.size
                            }
                    bucket.add(insertionPoint, audioItem)
                }
            }
            return map
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