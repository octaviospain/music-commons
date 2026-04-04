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

package net.transgressoft.commons.fx.music

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioLibrary
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next

/**
 * Creates audio items for multiple artists in an [AudioLibrary] and returns them grouped by artist.
 *
 * Each artist gets a default album named "{artistName} Album". All items are added to the library
 * via [AudioLibrary.createFromFile].
 *
 * @param artistConfigs Map of artist name to number of items to create for that artist
 * @return Map of [Artist] to list of created [ObservableAudioItem] instances
 */
fun AudioLibrary<ObservableAudioItem, *>.createItemsByArtist(
    artistConfigs: Map<String, Int>
): Map<Artist, List<ObservableAudioItem>> =
    artistConfigs.flatMap { (artistName, itemCount) ->
        val artist = ImmutableArtist.of(artistName)
        val album = ImmutableAlbum("$artistName Album", artist)
        List(itemCount) {
            createFromFile(
                Arb.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                }.next()
            )
        }.map { artist to it }
    }.groupBy({ it.first }, { it.second })

/**
 * Creates audio items for one artist across multiple albums in an [AudioLibrary].
 *
 * @param artistName The artist name
 * @param albumItemCounts Map of album name to number of items to create per album
 * @return List of all created [ObservableAudioItem] instances
 */
fun AudioLibrary<ObservableAudioItem, *>.createItemsWithMultipleAlbums(
    artistName: String,
    albumItemCounts: Map<String, Int>
): List<ObservableAudioItem> {
    val artist = ImmutableArtist.of(artistName)
    return albumItemCounts.flatMap { (albumName, count) ->
        val album = ImmutableAlbum(albumName, artist)
        List(count) {
            createFromFile(
                Arb.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                }.next()
            )
        }
    }
}