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
 * Concrete genre catalog type used internally by the audio library infrastructure.
 *
 * Extends [ReactiveGenreCatalog] as a typed marker for catalog instances managed by
 * the registry. Because an audio item may belong to multiple genres simultaneously,
 * a single item can appear in multiple genre catalog instances. Mutation operations
 * are internal to the concrete implementations.
 *
 * @param I The type of audio items contained in this catalog
 */
interface GenreCatalog<I : ReactiveAudioItem<I>> : ReactiveGenreCatalog<GenreCatalog<I>, I>, Comparable<GenreCatalog<I>>