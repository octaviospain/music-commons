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
 * A populated album managed by the audio library: the [AlbumDetails] identity plus the ordered list
 * of tracks that belong to it.
 *
 * This is the core-tier album type that [AudioLibrary] exposes and the registry projection produces,
 * binding [ReactiveAlbum]'s self-referential type parameter to the concrete album type so albums are
 * mutually comparable. Ordering of tracks within an album is owned by the concrete implementations.
 *
 * @param I The type of audio items contained in this album
 * @since 1.0
 */
public interface Album<I : ReactiveAudioItem<I>> : ReactiveAlbum<Album<I>, I>, Comparable<Album<I>>