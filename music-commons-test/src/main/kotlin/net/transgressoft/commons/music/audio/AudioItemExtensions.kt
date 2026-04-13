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

fun <I : ReactiveAudioItem<I>> I.update(change: AudioItemChange) {
    change.title?.let { title = it }
    change.artist?.let { artist = it }
    album =
        ImmutableAlbum(
            change.albumName ?: album.name,
            change.albumArtist ?: album.albumArtist,
            change.isCompilation ?: album.isCompilation,
            change.year?.takeIf { year -> year > 0 } ?: album.year,
            change.label ?: album.label
        )
    change.genres?.let { genres = it }
    change.comments?.let { comments = it }
    change.trackNumber?.takeIf { it > 0 }?.let { trackNumber = it }
    change.discNumber?.takeIf { it > 0 }?.let { discNumber = it }
    change.bpm?.takeIf { it > 0 }?.let { bpm = it }
    change.coverImageBytes?.let { coverImageBytes = it }
}