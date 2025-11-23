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

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.persistence.Repository
import java.util.*
import java.util.concurrent.Flow

/**
 * Manages a hierarchical structure of playlists with repository capabilities and event publishing.
 *
 * Supports creating, organizing, and moving playlists and playlist directories, as well as
 * adding and removing audio items from playlists within the hierarchy.
 */
interface PlaylistHierarchy<I: ReactiveAudioItem<I>, P: ReactiveAudioPlaylist<I, P>>: Repository<Int, P>, Flow.Publisher<CrudEvent<Int, P>> {

    val audioItemEventSubscriber: TransEventSubscriber<I, CrudEvent.Type, CrudEvent<Int, I>>

    @Throws(IllegalArgumentException::class)
    fun createPlaylist(name: String): P

    @Throws(IllegalArgumentException::class)
    fun createPlaylist(
        name: String,
        audioItems: List<I>
    ): P

    @Throws(IllegalArgumentException::class)
    fun createPlaylistDirectory(name: String): P

    @Throws(IllegalArgumentException::class)
    fun createPlaylistDirectory(
        name: String,
        audioItems: List<I>
    ): P

    fun findByName(name: String): Optional<out P>

    fun findParentPlaylist(playlist: ReactiveAudioPlaylist<I, P>): Optional<out P>

    fun movePlaylist(
        playlistNameToMove: String,
        destinationPlaylistName: String
    )

    fun addAudioItemToPlaylist(
        audioItem: I,
        playlistName: String
    ): Boolean = addAudioItemsToPlaylist(listOf(audioItem), playlistName)

    fun addAudioItemsToPlaylist(
        audioItems: Collection<I>,
        playlistName: String
    ): Boolean

    fun removeAudioItemFromPlaylist(
        audioItem: I,
        playlistName: String
    ): Boolean = removeAudioItemsFromPlaylist(listOf(audioItem), playlistName)

    fun removeAudioItemFromPlaylist(
        audioItemId: Int,
        playlistName: String
    ): Boolean = removeAudioItemsFromPlaylist(listOf(audioItemId), playlistName)

    fun removeAudioItemsFromPlaylist(
        audioItems: Collection<I>,
        playlistName: String
    ): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIdsFromPlaylist")
    fun removeAudioItemsFromPlaylist(
        audioItemIds: Collection<Int>,
        playlistName: String
    ): Boolean

    fun addPlaylistToDirectory(
        playlistToAdd: P,
        directoryName: String
    ): Boolean = addPlaylistsToDirectory(setOf(playlistToAdd), directoryName)

    fun addPlaylistToDirectory(
        playlistNameToAdd: String,
        directoryName: String
    ): Boolean = addPlaylistsToDirectory(setOf(playlistNameToAdd), directoryName)

    fun addPlaylistsToDirectory(
        playlistsToAdd: Set<P>,
        directoryName: String
    ): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addPlaylistNamesToDirectory")
    fun addPlaylistsToDirectory(
        playlistNamesToAdd: Set<String>,
        directoryName: String
    ): Boolean

    fun removePlaylistFromDirectory(
        playlistToRemove: P,
        directoryName: String
    ): Boolean = removePlaylistsFromDirectory(setOf(playlistToRemove), directoryName)

    fun removePlaylistFromDirectory(
        playlistNameToRemove: String,
        directoryName: String
    ): Boolean = removePlaylistsFromDirectory(setOf(playlistNameToRemove), directoryName)

    fun removePlaylistsFromDirectory(
        playlistsToRemove: Set<P>,
        directoryName: String
    ): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistNamesFromDirectory")
    fun removePlaylistsFromDirectory(
        playlistsNamesToRemove: Set<String>,
        directoryName: String
    ): Boolean

    fun numberOfPlaylists(): Int

    fun numberOfPlaylistDirectories(): Int
}