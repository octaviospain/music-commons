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

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.lirp.entity.ReactiveEntity

/**
 * Reactive extension of [AudioPlaylist] that allows mutation of the playlist and observation of changes.
 *
 * Provides methods to add and remove audio items and nested playlists.
 */
interface ReactiveAudioPlaylist<I : ReactiveAudioItem<I>, P : ReactiveAudioPlaylist<I, P>> : AudioPlaylist<I>, ReactiveEntity<Int, P> {

    /** Mutable display name of this playlist. Changing this value publishes a reactive mutation event. */
    override var name: String

    /** Mutable directory flag. Changing this value publishes a reactive mutation event. */
    override var isDirectory: Boolean

    /**
     * Adds [audioItem] to this playlist. Convenience delegate for [addAudioItems].
     *
     * @return `true` if the playlist changed as a result of this call
     */
    fun addAudioItem(audioItem: I): Boolean = addAudioItems(listOf(audioItem))

    /**
     * Adds the given audio items to this playlist. Duplicate items are allowed — if an item
     * already exists in the playlist, it will be added again.
     *
     * @return `true` if any existing item in the playlist is not contained in the given collection
     */
    fun addAudioItems(audioItems: Collection<I>): Boolean

    /**
     * Removes [audioItem] from this playlist. Convenience delegate for [removeAudioItems].
     *
     * @return `true` if the playlist changed as a result of this call
     */
    fun removeAudioItem(audioItem: I): Boolean = removeAudioItems(listOf(audioItem))

    /**
     * Removes the audio item with [audioItemId] from this playlist. Convenience delegate for [removeAudioItems].
     *
     * @return `true` if the playlist changed as a result of this call
     */
    fun removeAudioItem(audioItemId: Int): Boolean = removeAudioItems(listOf(audioItemId))

    /**
     * Removes all occurrences of each item in [audioItems] from this playlist.
     *
     * @return `true` if the playlist changed as a result of this call
     */
    fun removeAudioItems(audioItems: Collection<I>): Boolean

    // @JvmName required on generic interface methods to avoid JVM signature clashes with Java callers

    /**
     * Removes audio items whose IDs are in [audioItemIds] from this playlist.
     *
     * @return `true` if the playlist changed as a result of this call
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIds")
    fun removeAudioItems(audioItemIds: Collection<Int>): Boolean

    /**
     * Adds [playlist] as a nested child of this directory playlist. Convenience delegate for [addPlaylists].
     *
     * @return `true` if the set of nested playlists changed as a result of this call
     */
    fun addPlaylist(playlist: P): Boolean = addPlaylists(listOf(playlist))

    /**
     * Adds all playlists in [playlists] as nested children of this directory playlist.
     *
     * @return `true` if the set of nested playlists changed as a result of this call
     */
    fun addPlaylists(playlists: Collection<P>): Boolean

    /**
     * Removes the nested playlist with [playlistId] from this directory playlist. Convenience delegate for [removePlaylists].
     *
     * @return `true` if the set of nested playlists changed as a result of this call
     */
    fun removePlaylist(playlistId: Int): Boolean = removePlaylists(listOf(playlistId))

    /**
     * Removes [playlist] from this directory playlist. Convenience delegate for [removePlaylists].
     *
     * @return `true` if the set of nested playlists changed as a result of this call
     */
    fun removePlaylist(playlist: P): Boolean = removePlaylists(listOf(playlist))

    /**
     * Removes all playlists in [playlists] from this directory playlist.
     *
     * @return `true` if the set of nested playlists changed as a result of this call
     */
    fun removePlaylists(playlists: Collection<P>): Boolean

    /**
     * Removes nested playlists whose IDs are in [playlistIds] from this directory playlist.
     *
     * @return `true` if the set of nested playlists changed as a result of this call
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    fun removePlaylists(playlistIds: Collection<Int>): Boolean

    /** Removes all audio items from this playlist, leaving it empty. */
    fun clearAudioItems()

    /** Removes all nested playlists from this directory playlist. */
    fun clearPlaylists()

    /** The set of nested playlists directly contained within this playlist directory. */
    override val playlists: Set<P>
}