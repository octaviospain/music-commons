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

package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.ReactiveAudioLibrary
import net.transgressoft.commons.music.playlist.ReactiveAudioPlaylist
import net.transgressoft.commons.music.playlist.ReactivePlaylistHierarchy
import java.nio.file.Path
import java.util.Optional

/**
 * Facade for managing an audio library and playlist hierarchy as a unified entry point.
 *
 * Provides convenience methods for common operations (creating audio items from files,
 * managing playlists) while delegating to the underlying [ReactiveAudioLibrary] and
 * [ReactivePlaylistHierarchy] for advanced use cases.
 *
 * Core and JavaFX implementations are available via their respective builders.
 *
 * @param I The concrete audio item type
 * @param P The concrete playlist type
 */
interface MusicLibrary<I : ReactiveAudioItem<I>, P : ReactiveAudioPlaylist<I, P>> : AutoCloseable {

    /**
     * Returns the underlying audio library for direct access to audio item management.
     */
    fun audioLibrary(): ReactiveAudioLibrary<I, *>

    /**
     * Returns the underlying playlist hierarchy for direct access to playlist management.
     */
    fun playlistHierarchy(): ReactivePlaylistHierarchy<I, P>

    /**
     * Creates an audio item from the audio file at [path] and adds it to the audio library.
     *
     * @param path the path to the audio file
     * @return the created audio item
     */
    fun audioItemFromFile(path: Path): I

    /**
     * Creates a new playlist with [name].
     *
     * @param name the unique playlist name
     * @return the created playlist
     * @throws IllegalArgumentException if a playlist with [name] already exists
     */
    fun createPlaylist(name: String): P

    /**
     * Creates a new playlist with [name] pre-populated with audio items identified by [audioItemIds].
     *
     * @param name the unique playlist name
     * @param audioItemIds the IDs of audio items to include in the playlist
     * @return the created playlist
     * @throws IllegalArgumentException if a playlist with [name] already exists
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createPlaylistWithIds")
    fun createPlaylist(name: String, audioItemIds: List<Int>): P

    /**
     * Creates a new playlist directory with [name].
     *
     * @param name the unique directory name
     * @return the created playlist directory
     * @throws IllegalArgumentException if a playlist with [name] already exists
     */
    fun createPlaylistDirectory(name: String): P

    /**
     * Moves the playlist named [playlistNameToMove] into the directory named [destinationPlaylistName].
     *
     * @param playlistNameToMove the name of the playlist to move
     * @param destinationPlaylistName the name of the destination playlist directory
     */
    fun movePlaylist(playlistNameToMove: String, destinationPlaylistName: String)

    /**
     * Finds the playlist with the given [name].
     *
     * @param name the playlist name to search for
     * @return an [Optional] containing the playlist, or empty if not found
     */
    fun findPlaylistByName(name: String): Optional<out P>
}