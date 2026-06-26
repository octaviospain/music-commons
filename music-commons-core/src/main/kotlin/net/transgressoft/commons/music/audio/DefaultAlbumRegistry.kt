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

import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.projection.ObservableProjection
import net.transgressoft.lirp.persistence.projection.registryProjection

/**
 * Album registry backed by a lirp single-key value-transform registry projection.
 *
 * Builds one [Album] per album using the single-key form of [registryProjection]:
 * each audio item is placed into exactly one album bucket determined by its `album` property.
 * When an item's album changes, lirp re-keys the item from the old bucket to the new one
 * automatically. The projection maintains each bucket's track list in disc-then-track order via
 * [audioItemTrackDiscNumberComparator]. Shared CRUD-event republishing, album queries, and
 * lifecycle live in [AlbumRegistryBase].
 *
 * Unlike the genre index (which uses a multi-key projection because items can belong to
 * multiple genres), an item always belongs to exactly one album, making single-key sufficient.
 *
 * @param I The audio item type
 * @param repository The audio-item repository to project
 */
internal class DefaultAlbumRegistry<I>(repository: Repository<Int, I>)
: AlbumRegistryBase<I, Album<I>>("AlbumRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I> {

    override val projection: ObservableProjection<AlbumDetails, Album<I>> =
        registryProjection(
            registry = repository,
            keyExtractor = { it.album },
            entryOrdering = audioItemTrackDiscNumberComparator()
        ) { albumDetails, tracks -> ImmutableAlbum(albumDetails, tracks) }

    init {
        observeAlbumChanges(projection)
    }
}