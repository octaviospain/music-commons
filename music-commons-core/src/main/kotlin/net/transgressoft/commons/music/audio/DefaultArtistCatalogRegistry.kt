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
import net.transgressoft.lirp.persistence.projection.registryMultiKeyProjection

/**
 * Artist catalog registry backed by a lirp value-transform multi-key registry projection.
 *
 * Builds one [ArtistCatalog] per involved artist directly in the projection's value transform: an
 * item is placed under every artist in its `artistsInvolved` set, so a track that features additional
 * artists appears in each of their catalogs — consistent with the audio library's artist set and
 * `containsAudioItemWithArtist`, which also reason over `artistsInvolved`. Shared CRUD-event
 * republishing, catalog queries, and lifecycle live in [ArtistCatalogRegistryBase].
 *
 * @param I The audio item type
 * @param repository The audio-item repository to project
 */
internal class DefaultArtistCatalogRegistry<I>(repository: Repository<Int, I>)
: ArtistCatalogRegistryBase<I, ArtistCatalog<I>>("ArtistCatalogRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I> {

    override val projection: ObservableProjection<Artist, ArtistCatalog<I>> =
        registryMultiKeyProjection(
            registry = repository,
            keyExtractor = { it.artistsInvolved },
            entryOrdering = audioItemTrackDiscNumberComparator()
        ) { artist, items -> ImmutableArtistCatalog(artist, items) }

    init {
        observeCatalogChanges(projection)
    }
}