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

/**
 * JavaFX-specific artist catalog registry that creates [FXArtistCatalog] instances directly.
 *
 * Each catalog owns its audio item state and manages its own JavaFX observable properties.
 * Mutation operations are dispatched to [FXArtistCatalog] internal methods.
 */
internal class FXArtistCatalogRegistry :
    ArtistCatalogRegistryBase<ObservableAudioItem, ObservableArtistCatalog>("FXArtistCatalogRegistry") {

    override fun createCatalog(artist: Artist): ObservableArtistCatalog = FXArtistCatalog(artist)

    override fun ObservableArtistCatalog.addItem(audioItem: ObservableAudioItem): Boolean =
        (this as FXArtistCatalog).addAudioItem(audioItem)

    override fun ObservableArtistCatalog.removeItem(audioItem: ObservableAudioItem): Boolean =
        (this as FXArtistCatalog).removeAudioItem(audioItem)

    override fun ObservableArtistCatalog.merge(audioItem: ObservableAudioItem): Boolean =
        (this as FXArtistCatalog).mergeAudioItem(audioItem)

    override fun ObservableArtistCatalog.containsItem(audioItem: ObservableAudioItem): Boolean =
        (this as FXArtistCatalog).containsAudioItem(audioItem)

    override fun ObservableArtistCatalog.cloneCatalog(): ObservableArtistCatalog =
        (this as FXArtistCatalog).clone()
}