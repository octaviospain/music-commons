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

/**
 * Immutable album holding an ordered list of audio items for a single [AlbumDetails] key.
 *
 * Items are delivered pre-sorted by the projection's [entryOrdering] comparator (disc then track
 * number). The list is stored verbatim — no re-sorting or deduplication happens here because the
 * lirp projection guarantees both ordering and distinctness via its internal reverse index.
 *
 * The [album] field is the derived representative for this bucket (most-frequent field values
 * across all tracks), which preserves original name casing and carries the majority year/label.
 * [uniqueId] is derived from the canonical key (not the representative) so that it remains stable
 * when the representative's year or casing changes — e.g., correcting a single mistag does not
 * change the album's identity in event routing.
 *
 * Cover bytes are resolved lazily from the first track that carries cover data, cached behind a
 * [SoftReference] so that memory pressure can evict the bytes and re-resolution happens
 * transparently on the next access.
 *
 * Documented behavior: `Artist`, `Album`, and `Label` flyweight instances are cached in
 * [SoftReference]-backed maps and may be GC-evicted under memory pressure. After eviction a
 * subsequent lookup creates a new instance that is `equals`-equivalent but not `===`-identical
 * to the prior one. Always use value-based `equals` (or standard collection operations) to compare
 * flyweight instances; reference equality (`===`) is not a stable identity across GC cycles.
 *
 * @param I The type of audio items contained in this album
 */
internal class ImmutableAlbum<I>(override val album: AlbumDetails, tracks: List<I>) :
    Album<I>,
    ReactiveAlbum<Album<I>, I>,
    Comparable<Album<I>>,
    ReactiveEntityBase<AlbumDetails, Album<I>>()
    where I : ReactiveAudioItem<I> {

    private val logger = KotlinLogging.logger {}

    // Defensive copy; lirp delivers the list pre-sorted so no re-sorting is needed.
    override val tracks: List<I> = tracks.toList()

    override val id: AlbumDetails = album

    override val uniqueId: String = album.canonicalKey().id()

    override val isEmpty: Boolean
        get() = tracks.isEmpty()

    override val size: Int
        get() = tracks.size

    @Volatile
    private var coverRef: SoftReference<ByteArray>? = null

    // `noCover` means "no cover was found at the last load attempt".
    // It is reset whenever `coverRef` had been set (a cover was found previously) and is then
    // evicted by the GC — indicated by a non-null `coverRef` whose referent is null — so that
    // the next access retries loading from the tracks rather than returning null permanently.
    @Volatile
    private var noCover: Boolean = false

    override val coverImageBytes: ByteArray?
        get() {
            val currentRef = coverRef
            val cached = currentRef?.get()
            if (cached != null) return cached
            // If coverRef was previously set (a cover was loaded) but has since been evicted,
            // reset noCover so the next synchronized block retries loading.
            if (currentRef != null && cached == null) noCover = false
            if (noCover) return null
            synchronized(this) {
                val lockedRef = coverRef
                lockedRef?.get()?.let { return it }
                // Inner eviction check: re-clear noCover if coverRef was evicted while waiting.
                if (lockedRef != null && lockedRef.get() == null) noCover = false
                if (noCover) return null
                val bytes = firstCoverImageBytes(tracks)
                return if (bytes != null) {
                    coverRef = SoftReference(bytes)
                    bytes
                } else {
                    noCover = true
                    null
                }
            }
        }

    override fun compareTo(other: Album<I>): Int = this.album.compareTo(other.album)

    override fun clone(): ImmutableAlbum<I> = this

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImmutableAlbum<*>
        if (album != other.album) return false
        return tracks == other.tracks
    }

    override fun hashCode(): Int = 31 * album.hashCode() + tracks.hashCode()

    override fun toString() = "ImmutableAlbum(album=$album, size=$size)"

    init {
        logger.trace { "Album created for ${album.name}" }
    }
}