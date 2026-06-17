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
 * Organizes audio items by album and maintains them sorted by disc and track number.
 * The album-grouped structure is built in the constructor and never mutated afterwards,
 * so all reads are lock-free.
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

    // Built once from the snapshot; insertion-sorted and de-duplicated, so reads need no lock.
    private val audioItemsByAlbumName: Map<String, List<I>> = buildAlbumMap(audioItems)

    override val id: Artist = artist

    override val uniqueId: String = artist.id()

    override val albums: Set<AlbumSet<I>>
        get() =
            audioItemsByAlbumName.values
                .asSequence()
                .flatten()
                .distinctBy(::audioItemIdentity)
                .groupBy { it.album.name }
                .mapNotNull { (albumName, items) ->
                    if (items.isEmpty()) {
                        null
                    } else {
                        runCatching { AlbumView(albumName, items) }.getOrNull()
                    }
                }.toSet()

    override fun albumAudioItems(albumName: String): Set<I> =
        audioItemsByAlbumName[albumName]?.toSet() ?: emptySet()

    override val isEmpty: Boolean
        get() = audioItemsByAlbumName.isEmpty()

    override val size: Int
        get() = audioItemsByAlbumName.values.sumOf { it.size }

    private fun audioItemIdentity(audioItem: I): Any =
        if (audioItem.id != UNASSIGNED_ID)
            audioItem.id
        else
            audioItem.uniqueId

    override fun compareTo(other: ArtistCatalog<I>): Int = this.artist.compareTo(other.artist)

    override fun clone(): ImmutableArtistCatalog<I> = this

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImmutableArtistCatalog<*>
        if (artist != other.artist) return false
        return audioItemsByAlbumName == other.audioItemsByAlbumName
    }

    override fun hashCode(): Int = 31 * artist.hashCode() + audioItemsByAlbumName.hashCode()

    override fun toString() = "ImmutableArtistCatalog(artist=$artist, size=$size)"

    init {
        logger.debug { "Artist catalog created for ${artist.id()}" }
    }

    companion object {

        private fun <I> buildAlbumMap(audioItems: List<I>): Map<String, List<I>> where I : ReactiveAudioItem<I> {
            // Build sorted, de-duplicated per-album lists in one pass.
            // Insertion sort preserves the natural compareTo order; isSameAudioItem guards against duplicates.
            val map = sortedMapOf<String, MutableList<I>>()
            for (audioItem in audioItems) {
                val bucket = map.getOrPut(audioItem.album.name) { mutableListOf() }
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