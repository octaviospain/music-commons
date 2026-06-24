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

import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.GenreCatalogRegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.fx.projection.FxObservableProjection
import net.transgressoft.lirp.persistence.fx.projection.registryFxMultiKeyProjection

/**
 * JavaFX genre catalog registry backed by a two-phase value-transform multi-key registry projection.
 *
 * Groups audio items from [repository] by every genre they are tagged with, so a track with
 * multiple genres appears in each of the corresponding genre catalogs. Items with an empty genres
 * set appear in no genre catalog. The two-phase split keeps [FXGenreCatalog] construction
 * thread-safe:
 *
 * - **dataTransform** (background thread): pure snapshot of the bucket as a `List<ObservableAudioItem>`;
 *   must not touch any JavaFX property or node.
 * - **fxFactory** (FX Application Thread): constructs the [FXGenreCatalog] from the snapshot, safe to
 *   initialize JavaFX properties.
 *
 * Shared CRUD-event republishing, catalog queries, and lifecycle live in [GenreCatalogRegistryBase];
 * the projection's entries-changed callback fires on the FX Application Thread.
 *
 * @param repository The observable audio-item repository to project
 */
internal class FXGenreCatalogRegistry(repository: Repository<Int, ObservableAudioItem>)
: GenreCatalogRegistryBase<ObservableAudioItem, ObservableGenreCatalog>("FXGenreCatalogRegistry") {

    override val projection: FxObservableProjection<Genre, ObservableGenreCatalog> =
        registryFxMultiKeyProjection(
            registry = repository,
            keyExtractor = ObservableAudioItem::genres,
            dataTransform = { _, items -> items.toList() },
            fxFactory = { genre, data -> FXGenreCatalog(genre, data) },
            dispatchToFxThread = true
        )

    init {
        observeCatalogChanges(projection)
    }
}