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

import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.ArtistCatalogRegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.fx.projection.FxObservableProjection
import net.transgressoft.lirp.persistence.fx.projection.registryFxMultiKeyProjection

/**
 * JavaFX artist catalog registry backed by a two-phase value-transform multi-key registry projection.
 *
 * Groups audio items from [repository] by every artist involved in each item (its `artistsInvolved`
 * set), so a track that features additional artists appears in each of their catalogs. The two-phase
 * split keeps [FXArtistCatalog] construction thread-safe:
 *
 * - **dataTransform** (background thread): pure snapshot of the bucket as a `List<ObservableAudioItem>`;
 *   must not touch any JavaFX property or node.
 * - **fxFactory** (FX Application Thread): constructs the [FXArtistCatalog] from the snapshot, safe to
 *   initialize JavaFX properties.
 *
 * Shared CRUD-event republishing, catalog queries, and lifecycle live in [ArtistCatalogRegistryBase];
 * the projection's entries-changed callback fires on the FX Application Thread.
 *
 * @param repository The observable audio-item repository to project
 */
internal class FXArtistCatalogRegistry(repository: Repository<Int, ObservableAudioItem>)
: ArtistCatalogRegistryBase<ObservableAudioItem, ObservableArtistCatalog>("FXArtistCatalogRegistry") {

    override val projection: FxObservableProjection<Artist, ObservableArtistCatalog> =
        registryFxMultiKeyProjection(
            registry = repository,
            keyExtractor = ObservableAudioItem::artistsInvolved,
            dataTransform = { _, items -> items.toList() },
            fxFactory = { artist, data -> FXArtistCatalog(artist, data) },
            dispatchToFxThread = true
        )

    init {
        observeCatalogChanges(projection)
    }
}