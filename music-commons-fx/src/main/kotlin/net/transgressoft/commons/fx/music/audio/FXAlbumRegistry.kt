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
import net.transgressoft.commons.music.audio.albumBucketComparator
import net.transgressoft.commons.music.audio.audioItemTrackDiscNumberComparator
import net.transgressoft.commons.music.audio.canonicalKey
import net.transgressoft.commons.music.audio.deriveRepresentativeAlbumDetails
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.fx.projection.FxObservableProjection
import net.transgressoft.lirp.persistence.fx.projection.registryFxProjection

/**
 * JavaFX album registry backed by a two-phase value-transform single-key registry projection.
 *
 * Groups audio items from [repository] by their canonical album identity, building one [FXAlbum]
 * per logical album. The bucket key is the canonical [AlbumDetails] (normalized name +
 * compilation-aware artist, with year/label/isCompilation zeroed), so tracks whose per-track
 * metadata varies in year, label, or albumArtist still land in the same bucket. The bucket's
 * exposed [AlbumDetails] is a derived representative (most-frequent non-empty value per field)
 * — not the canonical key.
 *
 * Each item has exactly one album, so the single-key projection form is used. The two-phase
 * split keeps [FXAlbum] construction thread-safe:
 *
 * - **dataTransform** (background thread): derives the representative [AlbumDetails] and captures
 *   a snapshot of the bucket; must not touch any JavaFX property or node.
 * - **fxFactory** (FX Application Thread): constructs the [FXAlbum] from the snapshot, safe to
 *   initialize JavaFX properties.
 *
 * The projection maintains each bucket's list in disc-then-track order via
 * [audioItemTrackDiscNumberComparator] and orders buckets by album name then artist (blank last)
 * via [albumBucketComparator], applied before the FX-thread pulse. Shared CRUD-event republishing,
 * album queries, and lifecycle live in [AlbumRegistryBase]; the projection's entries-changed
 * callback fires on the FX Application Thread.
 *
 * @param repository The observable audio-item repository to project
 */
internal class FXAlbumRegistry(repository: Repository<Int, ObservableAudioItem>)
: AlbumRegistryBase<ObservableAudioItem, ObservableAlbum>("FXAlbumRegistry") {

    override val projection: FxObservableProjection<AlbumDetails, ObservableAlbum> =
        registryFxProjection(
            registry = repository,
            keyExtractor = { it.album.canonicalKey() },
            // dataTransform runs off the FX thread — safe for pure computation including
            // deriveRepresentativeAlbumDetails, which touches no JavaFX state.
            dataTransform = { _, items -> Pair(deriveRepresentativeAlbumDetails(items), items.toList()) },
            fxFactory = { _, (representative, data) -> FXAlbum(representative, data) },
            dispatchToFxThread = true,
            entryOrdering = audioItemTrackDiscNumberComparator(),
            bucketValueOrdering = albumBucketComparator<ObservableAlbum>()
        )

    init {
        observeAlbumChanges(projection)
    }
}