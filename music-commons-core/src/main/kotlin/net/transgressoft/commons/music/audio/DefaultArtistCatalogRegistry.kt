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

/**
 * Core registry managing artist catalogs with [ArtistCatalog] instances.
 *
 * Creates [MutableArtistCatalog] instances internally and delegates mutation
 * operations to the [ArtistCatalog] interface methods.
 */
internal class DefaultArtistCatalogRegistry<I> :
    ArtistCatalogRegistryBase<I, ArtistCatalog<I>>("ArtistCatalogRegistry")
    where I : ReactiveAudioItem<I>, I : Comparable<I> {

    override fun createCatalog(artist: Artist): ArtistCatalog<I> = MutableArtistCatalog(artist)

    override fun ArtistCatalog<I>.addItem(audioItem: I) = (this as MutableArtistCatalog<I>).addAudioItem(audioItem)

    override fun ArtistCatalog<I>.removeItem(audioItem: I) = (this as MutableArtistCatalog<I>).removeAudioItem(audioItem)

    override fun ArtistCatalog<I>.merge(audioItem: I) = (this as MutableArtistCatalog<I>).mergeAudioItem(audioItem)

    override fun ArtistCatalog<I>.containsItem(audioItem: I) = (this as MutableArtistCatalog<I>).containsAudioItem(audioItem)

    override fun ArtistCatalog<I>.cloneCatalog() = (this as MutableArtistCatalog<I>).clone()
}