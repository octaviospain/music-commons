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

import net.transgressoft.commons.music.audio.MutableAudioItemTestBridge.createAudioItem
import net.transgressoft.lirp.event.EventType
import net.transgressoft.lirp.event.LirpEvent
import net.transgressoft.lirp.event.LirpEventPublisher
import net.transgressoft.lirp.persistence.Repository
import io.kotest.property.arbitrary.next

/*
 * Shared construction and event-collection scaffolding for the core audio-library projection specs
 * (artist catalog, album, genre-index registries and the audio-library facade).
 *
 * Centralizes the repeated `virtualAudioFile { ... }.next()` + `createAudioItem(path, metadataIO)`
 * arrange and the `mutableListOf<CrudEvent<..>>().also { publisher.subscribe(TYPE) { list.add(it) } }`
 * event-collector idiom used across the reactive/registry specs.
 */

/**
 * Builds an [AudioItem] from a virtual audio file tagged with the supplied fields, mirroring the
 * repeated `files.virtualAudioFile { ... }.next()` + [createAudioItem] arrange used across the
 * projection specs.
 *
 * @param artist the track artist
 * @param album the album the track belongs to
 * @param title the track title
 * @param genres the genres the track carries (empty means untagged)
 * @param track the track number within its disc
 * @param disc the disc number within the album
 * @return a [MutableAudioItem] backed by a materialized virtual file
 */
fun VirtualFiles.catalogItem(
    artist: Artist,
    album: AlbumDetails,
    title: String,
    genres: Set<Genre> = emptySet(),
    track: Short = 1,
    disc: Short = 1
): AudioItem =
    createAudioItem(
        virtualAudioFile {
            this.artist = artist
            this.album = album
            this.genres = genres
            this.title = title
            trackNumber = track
            discNumber = disc
        }.next(),
        metadataIO
    )

/**
 * Subscribes to [types] on this publisher and returns a mutable list that accumulates every matching
 * event, mirroring the `mutableListOf<..>().also { publisher.subscribe(TYPE) { it -> list.add(it) } }`
 * idiom repeated across the reactive specs.
 *
 * @param types the event types to collect (subscribing to all types when omitted)
 * @return a live list that grows as matching events are emitted
 */
fun <ET : EventType, E : LirpEvent<ET>> LirpEventPublisher<ET, E>.collect(vararg types: ET): MutableList<E> =
    mutableListOf<E>().also { list ->
        if (types.isEmpty()) subscribe { list.add(it) } else subscribe(*types) { list.add(it) }
    }

/**
 * Applies the seed-before-registry ceremony: closes the current [registry] via [close], seeds every
 * item into [repository], then rebuilds the registry via [build] over the fully-populated repository.
 *
 * Seeding before construction keeps the ordered projection off the incremental-insert path so the
 * ordering comparator reads only already-cached values during the seed. The projection registries
 * expose `close()` without sharing an `AutoCloseable` supertype, so the caller supplies [close].
 *
 * @param repository the repository to seed
 * @param registry the currently-open registry to close before rebuilding
 * @param items the items to seed, added in the order given
 * @param close closes the current registry (typically `{ it.close() }`)
 * @param build constructs a fresh registry over the now-populated repository
 * @return the rebuilt registry
 */
fun <R> seededRegistry(
    repository: Repository<Int, AudioItem>,
    registry: R,
    items: List<AudioItem>,
    close: (R) -> Unit,
    build: () -> R
): R {
    close(registry)
    items.forEach { repository.add(it) }
    return build()
}