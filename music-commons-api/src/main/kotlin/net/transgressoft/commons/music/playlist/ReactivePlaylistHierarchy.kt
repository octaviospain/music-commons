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
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventSubscriber
import net.transgressoft.lirp.persistence.Repository
import java.util.*
import java.util.concurrent.Flow

/**
 * Generic hierarchical structure of playlists with repository capabilities and event publishing.
 *
 * Supports creating, organizing, and moving playlists and playlist directories, as well as
 * adding and removing audio items from playlists within the hierarchy.
 *
 * Narrowed versions for concrete item types are available in the core and FX modules.
 * @since 1.0
 */
public interface ReactivePlaylistHierarchy<I : ReactiveAudioItem<I>, P : ReactiveAudioPlaylist<I, P>> :
    Repository<Int, P>,
    LirpEventSubscriber<I, CrudEvent.Type, CrudEvent<Int, I>>,
    Flow.Publisher<CrudEvent<Int, P>> {

    /**
     * Creates and registers a new empty playlist with [name].
     *
     * @param name unique name for the new playlist
     * @return the newly created playlist
     * @throws IllegalArgumentException if a playlist with [name] already exists
     * @since 1.0
     */
    @Throws(IllegalArgumentException::class)
    public fun createPlaylist(name: String): P

    /**
     * Creates and registers a new playlist with [name] pre-populated with [audioItems].
     *
     * @param name unique name for the new playlist
     * @param audioItems typed audio items to include in the playlist
     * @return the newly created playlist
     * @throws IllegalArgumentException if a playlist with [name] already exists
     * @since 1.0
     */
    @Throws(IllegalArgumentException::class)
    public fun createPlaylist(
        name: String,
        audioItems: List<I>
    ): P

    /**
     * Creates a new playlist with the given [name] pre-populated with audio items identified by [audioItemIds].
     *
     * Enables callers that hold audio item IDs (e.g. from an external import source) to create
     * playlists without requiring typed item references. Follows the same overloading convention
     * as [removeAudioItemsFromPlaylist].
     *
     * @param name the unique playlist name
     * @param audioItemIds the IDs of audio items to include in the playlist
     * @return the created playlist
     * @throws IllegalArgumentException if a playlist with [name] already exists
     * @since 1.0
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createPlaylistWithIds")
    @Throws(IllegalArgumentException::class)
    public fun createPlaylist(
        name: String,
        audioItemIds: List<Int>
    ): P

    /**
     * Creates and registers a new empty playlist directory with [name].
     *
     * @param name unique name for the new directory
     * @return the newly created playlist directory
     * @throws IllegalArgumentException if a playlist with [name] already exists
     * @since 1.0
     */
    @Throws(IllegalArgumentException::class)
    public fun createPlaylistDirectory(name: String): P

    /**
     * Creates and registers a new playlist directory with [name] pre-populated with [audioItems].
     *
     * @param name unique name for the new directory
     * @param audioItems typed audio items to include in the directory playlist
     * @return the newly created playlist directory
     * @throws IllegalArgumentException if a playlist with [name] already exists
     * @since 1.0
     */
    @Throws(IllegalArgumentException::class)
    public fun createPlaylistDirectory(
        name: String,
        audioItems: List<I>
    ): P

    /**
     * Finds a playlist by its exact [name].
     *
     * @param name the name to search for
     * @return an [Optional] containing the matching playlist, or empty if not found
     * @since 1.0
     */
    public fun findByName(name: String): Optional<out P>

    /**
     * Finds the direct parent directory playlist that contains [playlist].
     *
     * @param playlist the playlist whose parent is to be located
     * @return an [Optional] containing the parent directory, or empty if [playlist] is a root-level entry
     * @since 1.0
     */
    public fun findParentPlaylist(playlist: ReactiveAudioPlaylist<I, P>): Optional<out P>

    /**
     * Moves the playlist named [playlistNameToMove] into the directory playlist named [destinationPlaylistName].
     *
     * @param playlistNameToMove name of the playlist to relocate
     * @param destinationPlaylistName name of the target directory playlist
     * @since 1.0
     */
    public fun movePlaylist(
        playlistNameToMove: String,
        destinationPlaylistName: String
    )

    /**
     * Adds [audioItem] to the playlist named [playlistName]. Convenience delegate for [addAudioItemsToPlaylist].
     *
     * @return `true` if the target playlist changed as a result of this call
     * @since 1.0
     */
    public fun addAudioItemToPlaylist(
        audioItem: I,
        playlistName: String
    ): Boolean = addAudioItemsToPlaylist(listOf(audioItem), playlistName)

    /**
     * Adds all items in [audioItems] to the playlist named [playlistName].
     *
     * @param audioItems items to add
     * @param playlistName name of the target playlist
     * @return `true` if the target playlist changed as a result of this call
     * @since 1.0
     */
    public fun addAudioItemsToPlaylist(
        audioItems: Collection<I>,
        playlistName: String
    ): Boolean

    /**
     * Removes [audioItem] from the playlist named [playlistName]. Convenience delegate for [removeAudioItemsFromPlaylist].
     *
     * @return `true` if the target playlist changed as a result of this call
     * @since 1.0
     */
    public fun removeAudioItemFromPlaylist(
        audioItem: I,
        playlistName: String
    ): Boolean = removeAudioItemsFromPlaylist(listOf(audioItem), playlistName)

    /**
     * Removes the audio item with [audioItemId] from the playlist named [playlistName].
     * Convenience delegate for [removeAudioItemsFromPlaylist].
     *
     * @return `true` if the target playlist changed as a result of this call
     * @since 1.0
     */
    public fun removeAudioItemFromPlaylist(
        audioItemId: Int,
        playlistName: String
    ): Boolean = removeAudioItemsFromPlaylist(listOf(audioItemId), playlistName)

    /**
     * Removes all items in [audioItems] from the playlist named [playlistName].
     *
     * @param audioItems items to remove
     * @param playlistName name of the target playlist
     * @return `true` if the target playlist changed as a result of this call
     * @since 1.0
     */
    public fun removeAudioItemsFromPlaylist(
        audioItems: Collection<I>,
        playlistName: String
    ): Boolean

    // @JvmName required on generic interface methods to avoid JVM signature clashes with Java callers

    /**
     * Removes audio items whose IDs are in [audioItemIds] from the playlist named [playlistName].
     *
     * @param audioItemIds IDs of items to remove
     * @param playlistName name of the target playlist
     * @return `true` if the target playlist changed as a result of this call
     * @since 1.0
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIdsFromPlaylist")
    public fun removeAudioItemsFromPlaylist(
        audioItemIds: Collection<Int>,
        playlistName: String
    ): Boolean

    /**
     * Adds [playlistToAdd] into the directory playlist named [directoryName].
     * Convenience delegate for [addPlaylistsToDirectory].
     *
     * @return `true` if the directory changed as a result of this call
     * @since 1.0
     */
    public fun addPlaylistToDirectory(
        playlistToAdd: P,
        directoryName: String
    ): Boolean = addPlaylistsToDirectory(setOf(playlistToAdd), directoryName)

    /**
     * Adds the playlist named [playlistNameToAdd] into the directory playlist named [directoryName].
     * Convenience delegate for [addPlaylistsToDirectory].
     *
     * @return `true` if the directory changed as a result of this call
     * @since 1.0
     */
    public fun addPlaylistToDirectory(
        playlistNameToAdd: String,
        directoryName: String
    ): Boolean = addPlaylistsToDirectory(setOf(playlistNameToAdd), directoryName)

    /**
     * Adds all playlists in [playlistsToAdd] into the directory playlist named [directoryName].
     *
     * @param playlistsToAdd playlists to nest inside the directory
     * @param directoryName name of the target directory playlist
     * @return `true` if the directory changed as a result of this call
     * @since 1.0
     */
    public fun addPlaylistsToDirectory(
        playlistsToAdd: Set<P>,
        directoryName: String
    ): Boolean

    /**
     * Adds playlists identified by name in [playlistNamesToAdd] into the directory playlist named [directoryName].
     *
     * @param playlistNamesToAdd names of playlists to nest inside the directory
     * @param directoryName name of the target directory playlist
     * @return `true` if the directory changed as a result of this call
     * @since 1.0
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addPlaylistNamesToDirectory")
    public fun addPlaylistsToDirectory(
        playlistNamesToAdd: Set<String>,
        directoryName: String
    ): Boolean

    /**
     * Removes [playlistToRemove] from the directory playlist named [directoryName].
     * Convenience delegate for [removePlaylistsFromDirectory].
     *
     * @return `true` if the directory changed as a result of this call
     * @since 1.0
     */
    public fun removePlaylistFromDirectory(
        playlistToRemove: P,
        directoryName: String
    ): Boolean = removePlaylistsFromDirectory(setOf(playlistToRemove), directoryName)

    /**
     * Removes the playlist named [playlistNameToRemove] from the directory playlist named [directoryName].
     * Convenience delegate for [removePlaylistsFromDirectory].
     *
     * @return `true` if the directory changed as a result of this call
     * @since 1.0
     */
    public fun removePlaylistFromDirectory(
        playlistNameToRemove: String,
        directoryName: String
    ): Boolean = removePlaylistsFromDirectory(setOf(playlistNameToRemove), directoryName)

    /**
     * Removes all playlists in [playlistsToRemove] from the directory playlist named [directoryName].
     *
     * @param playlistsToRemove playlists to detach from the directory
     * @param directoryName name of the target directory playlist
     * @return `true` if the directory changed as a result of this call
     * @since 1.0
     */
    public fun removePlaylistsFromDirectory(
        playlistsToRemove: Set<P>,
        directoryName: String
    ): Boolean

    /**
     * Removes playlists identified by name in [playlistsNamesToRemove] from the directory playlist named [directoryName].
     *
     * @param playlistsNamesToRemove names of playlists to detach from the directory
     * @param directoryName name of the target directory playlist
     * @return `true` if the directory changed as a result of this call
     * @since 1.0
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistNamesFromDirectory")
    public fun removePlaylistsFromDirectory(
        playlistsNamesToRemove: Set<String>,
        directoryName: String
    ): Boolean

    /**
     * Returns the total number of leaf playlists (non-directory) registered in this hierarchy.
     * @since 1.0
     */
    public fun numberOfPlaylists(): Int

    /**
     * Returns the total number of directory playlists registered in this hierarchy.
     * @since 1.0
     */
    public fun numberOfPlaylistDirectories(): Int
}