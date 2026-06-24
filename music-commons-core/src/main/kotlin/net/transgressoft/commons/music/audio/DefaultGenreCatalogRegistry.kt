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
 * Genre catalog registry backed by a lirp multi-key value-transform registry projection.
 *
 * Builds one [GenreCatalog] per genre using [registryMultiKeyProjection]: each audio item is
 * placed into every genre bucket corresponding to its `genres` set. An item that belongs to
 * multiple genres appears in each of those genre catalogs simultaneously. An item with an empty
 * `genres` set is placed in no genre bucket. Shared CRUD-event republishing, catalog queries,
 * and lifecycle live in [GenreCatalogRegistryBase].
 *
 * Unlike the album catalog (which uses single-key projection because items have exactly one album),
 * the multi-key projection is required here because items can belong to multiple genres.
 *
 * @param I The audio item type
 * @param repository The audio-item repository to project
 */
internal class DefaultGenreCatalogRegistry<I>(repository: Repository<Int, I>)
: GenreCatalogRegistryBase<I, GenreCatalog<I>>("GenreCatalogRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I> {

    override val projection: ObservableProjection<Genre, GenreCatalog<I>> =
        registryMultiKeyProjection(
            registry = repository,
            keyExtractor = { it.genres },
            valueTransform = { genre, items -> ImmutableGenreCatalog(genre, items) }
        )

    init {
        observeCatalogChanges(projection)
    }
}