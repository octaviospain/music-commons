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

import net.transgressoft.lirp.entity.ReactiveEntity

/**
 * Represents a reactive flat catalog of all audio items for a specific genre.
 *
 * Each catalog instance holds all audio items associated with a single [Genre] key.
 * Because an audio item may belong to multiple genres simultaneously, a single item
 * can appear in multiple genre catalogs at the same time. This interface extends
 * [ReactiveEntity] to support reactive updates when the catalog contents change.
 *
 * Unlike the artist catalog, there is no sub-grouping within the bucket: every audio
 * item tagged with this genre is exposed directly via [audioItems]. Items whose
 * [ReactiveAudioItem.genres] set is empty will not appear in any genre catalog.
 *
 * @param GC The concrete type of this genre catalog, for self-referential generics
 * @param I The type of audio items contained in this catalog
 */
interface ReactiveGenreCatalog<GC : ReactiveGenreCatalog<GC, I>, I : ReactiveAudioItem<I>> : ReactiveEntity<Genre, GC> {

    /**
     * The genre this catalog represents.
     */
    val genre: Genre

    /**
     * All audio items in this catalog, in natural comparable order, de-duplicated.
     */
    val audioItems: Set<I>

    /**
     * The total number of audio items in this catalog.
     */
    val size: Int

    /**
     * Indicates whether this catalog contains any audio items.
     */
    val isEmpty: Boolean
}