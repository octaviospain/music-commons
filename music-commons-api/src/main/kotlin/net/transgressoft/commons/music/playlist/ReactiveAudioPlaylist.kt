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

package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.entity.ReactiveEntity
import net.transgressoft.commons.music.audio.ReactiveAudioItem

/**
 * Reactive extension of [AudioPlaylist] that allows mutation of the playlist and observation of changes.
 *
 * Provides methods to add and remove audio items and nested playlists.
 */
interface ReactiveAudioPlaylist<I : ReactiveAudioItem<I>, P : ReactiveAudioPlaylist<I, P>> : AudioPlaylist<I>, ReactiveEntity<Int, P> {

    override var name: String

    override var isDirectory: Boolean

    fun addAudioItem(audioItem: I): Boolean = addAudioItems(listOf(audioItem))

    fun addAudioItems(audioItems: Collection<I>): Boolean

    fun removeAudioItem(audioItem: I): Boolean = removeAudioItems(listOf(audioItem))

    fun removeAudioItem(audioItemId: Int): Boolean = removeAudioItems(listOf(audioItemId))

    fun removeAudioItems(audioItems: Collection<I>): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIds")
    fun removeAudioItems(audioItemIds: Collection<Int>): Boolean

    fun addPlaylist(playlist: P): Boolean = addPlaylists(listOf(playlist))

    fun addPlaylists(playlists: Collection<P>): Boolean

    fun removePlaylist(playlistId: Int): Boolean = removePlaylists(listOf(playlistId))

    fun removePlaylist(playlist: P): Boolean = removePlaylists(listOf(playlist))

    fun removePlaylists(playlists: Collection<P>): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    fun removePlaylists(playlistIds: Collection<Int>): Boolean

    fun clearAudioItems()

    fun clearPlaylists()

    override val playlists: Set<P>
}