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
    change.genre ?: genre
    change.comments ?: comments
    change.trackNumber?.takeIf { trackNum -> trackNum > 0 } ?: trackNumber
    change.discNumber?.takeIf { discNum -> discNum > 0 } ?: discNumber
    change.bpm?.takeIf { bpm -> bpm > 0 } ?: bpm
    change.coverImageBytes ?: coverImageBytes
}