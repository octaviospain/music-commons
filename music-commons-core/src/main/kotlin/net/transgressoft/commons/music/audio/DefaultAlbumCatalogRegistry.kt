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
 * Album catalog registry backed by a lirp single-key value-transform registry projection.
 *
 * Builds one [AlbumCatalog] per album using the single-key form of [registryProjection]:
 * each audio item is placed into exactly one album bucket determined by its `album` property.
 * When an item's album changes, lirp re-keys the item from the old bucket to the new one
 * automatically. Shared CRUD-event republishing, catalog queries, and lifecycle live in
 * [AlbumCatalogRegistryBase].
 *
 * Unlike the genre catalog (which uses a multi-key projection because items can belong to
 * multiple genres), an item always belongs to exactly one album, making single-key sufficient.
 *
 * @param I The audio item type
 * @param repository The audio-item repository to project
 */
internal class DefaultAlbumCatalogRegistry<I>(repository: Repository<Int, I>)
: AlbumCatalogRegistryBase<I, AlbumCatalog<I>>("AlbumCatalogRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I> {

    override val projection: ObservableProjection<Album, AlbumCatalog<I>> =
        registryProjection(
            registry = repository,
            keyExtractor = { it.album },
            valueTransform = { album, items -> ImmutableAlbumCatalog(album, items) }
        )

    init {
        observeCatalogChanges(projection)
    }
}