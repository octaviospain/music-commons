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

package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.AlbumCatalogRegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.fx.projection.FxObservableProjection
import net.transgressoft.lirp.persistence.fx.projection.registryFxProjection

/**
 * JavaFX album catalog registry backed by a two-phase value-transform single-key registry projection.
 *
 * Groups audio items from [repository] by their album, building one [FXAlbumCatalog] per distinct
 * album. Each item has exactly one album, so the single-key projection form is used. The two-phase
 * split keeps [FXAlbumCatalog] construction thread-safe:
 *
 * - **dataTransform** (background thread): pure snapshot of the bucket as a `List<ObservableAudioItem>`;
 *   must not touch any JavaFX property or node.
 * - **fxFactory** (FX Application Thread): constructs the [FXAlbumCatalog] from the snapshot, safe to
 *   initialize JavaFX properties.
 *
 * Shared CRUD-event republishing, catalog queries, and lifecycle live in [AlbumCatalogRegistryBase];
 * the projection's entries-changed callback fires on the FX Application Thread.
 *
 * @param repository The observable audio-item repository to project
 */
internal class FXAlbumCatalogRegistry(repository: Repository<Int, ObservableAudioItem>)
: AlbumCatalogRegistryBase<ObservableAudioItem, ObservableAlbumCatalog>("FXAlbumCatalogRegistry") {

    override val projection: FxObservableProjection<Album, ObservableAlbumCatalog> =
        registryFxProjection(
            registry = repository,
            keyExtractor = { it.album },
            dataTransform = { _, items -> items.toList() },
            fxFactory = { album, data -> FXAlbumCatalog(album, data) },
            dispatchToFxThread = true
        )

    init {
        observeCatalogChanges(projection)
    }
}