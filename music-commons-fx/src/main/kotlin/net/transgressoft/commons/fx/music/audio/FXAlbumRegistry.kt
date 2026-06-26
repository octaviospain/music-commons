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

import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.AlbumRegistryBase
import net.transgressoft.commons.music.audio.audioItemTrackDiscNumberComparator
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.fx.projection.FxObservableProjection
import net.transgressoft.lirp.persistence.fx.projection.registryFxProjection

/**
 * JavaFX album registry backed by a two-phase value-transform single-key registry projection.
 *
 * Groups audio items from [repository] by their album, building one [FXAlbum] per distinct
 * album. Each item has exactly one album, so the single-key projection form is used. The two-phase
 * split keeps [FXAlbum] construction thread-safe:
 *
 * - **dataTransform** (background thread): pure snapshot of the bucket as a `List<ObservableAudioItem>`;
 *   must not touch any JavaFX property or node.
 * - **fxFactory** (FX Application Thread): constructs the [FXAlbum] from the snapshot, safe to
 *   initialize JavaFX properties.
 *
 * The projection maintains each bucket's list in disc-then-track order via
 * [audioItemTrackDiscNumberComparator]. Shared CRUD-event republishing, album queries, and
 * lifecycle live in [AlbumRegistryBase]; the projection's entries-changed callback fires on
 * the FX Application Thread.
 *
 * @param repository The observable audio-item repository to project
 */
internal class FXAlbumRegistry(repository: Repository<Int, ObservableAudioItem>)
: AlbumRegistryBase<ObservableAudioItem, ObservableAlbum>("FXAlbumRegistry") {

    override val projection: FxObservableProjection<AlbumDetails, ObservableAlbum> =
        registryFxProjection(
            registry = repository,
            keyExtractor = { it.album },
            dataTransform = { _, items -> items.toList() },
            fxFactory = { albumDetails, data -> FXAlbum(albumDetails, data) },
            dispatchToFxThread = true,
            entryOrdering = audioItemTrackDiscNumberComparator()
        )

    init {
        observeAlbumChanges(projection)
    }
}