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

import net.transgressoft.commons.entity.toIds
import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemManipulationException
import net.transgressoft.commons.music.audio.AudioLibrary
import net.transgressoft.commons.persistence.Repository
import net.transgressoft.commons.persistence.VolatileRepository
import mu.KotlinLogging
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Default implementation of [PlaylistHierarchy] for managing [MutableAudioPlaylist] instances.
 *
 * Handles deserialization of persisted playlists by reconstructing audio item references
 * and playlist hierarchies from stored IDs. The audio library parameter enables resolution
 * of audio item IDs to actual audio item instances during initialization.
 */
class DefaultPlaylistHierarchy(
    repository: Repository<Int, MutableAudioPlaylist> = VolatileRepository(),
    // Only needed when the provided repository is not empty
    audioLibrary: AudioLibrary<AudioItem>? = null
): PlaylistHierarchyBase<AudioItem, MutableAudioPlaylist>(repository) {
    private val logger = KotlinLogging.logger {}

    init {
        require(repository.isEmpty || (repository.isEmpty.not() && audioLibrary != null)) {
            "AudioLibrary is required when loading a non empty playlistHierarchy"
        }

        disableEvents(CREATE, UPDATE, DELETE) // disable events until the initial load from the file is completed
        runForAll {
            val playlistWithAudioItems = MutablePlaylist(it.id, it.isDirectory, it.name, mapAudioItemsFromIds(it.audioItems.toIds(), audioLibrary!!))
            // Remove the item from the repository to delete the subscription
            remove(it)
            add(playlistWithAudioItems)
        }
        runForAll {
            val playlistMissingPlaylists =
                repository.findById(it.id).orElseThrow {
                    IllegalStateException("Playlist ID ${it.id} not found after initial processing")
                }
            val foundPlaylists = findDeserializedPlaylistsFromIds(it.playlists.toIds(), repository)
            playlistMissingPlaylists.addPlaylists(foundPlaylists)
        }

        activateEvents(CREATE, UPDATE, DELETE)
    }

    private fun mapAudioItemsFromIds(
        audioItemIds: List<Int>,
        audioLibrary: AudioLibrary<AudioItem>
    ) = audioItemIds.map {
        audioLibrary.findById(it)
            .orElseThrow { AudioItemManipulationException("AudioItem with id $it not found during deserialization") }
    }.toList()

    private fun findDeserializedPlaylistsFromIds(
        playlists: Set<Int>,
        repository: Repository<Int, MutableAudioPlaylist>
    ): List<MutableAudioPlaylist> =
        playlists.stream().map {
            repository.findById(it)
                .orElseThrow { AudioItemManipulationException("AudioPlaylist with id $it not found during deserialization") }
        }.toList()

    override fun createPlaylist(name: String): MutableAudioPlaylist = createPlaylist(name, emptyList())

    override fun createPlaylist(
        name: String,
        audioItems: List<AudioItem>
    ): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return MutablePlaylist(newId(), false, name, audioItems).also {
            logger.debug { "Created playlist $it" }
            add(it)
        }
    }

    override fun createPlaylistDirectory(name: String): MutableAudioPlaylist = createPlaylistDirectory(name, emptyList())

    override fun createPlaylistDirectory(
        name: String,
        audioItems: List<AudioItem>
    ): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return MutablePlaylist(newId(), true, name, audioItems).also {
            logger.debug { "Created playlist directory $it" }
            add(it)
        }
    }

    override fun toString() = "PlaylistRepository(playlistsCount=${size()})"

    /**
     * Concrete implementation of [MutableAudioPlaylist] bound to this hierarchy.
     *
     * Implemented as an inner class to inherit hierarchy management capabilities from
     * [MutablePlaylistBase] while accessing the enclosing [DefaultPlaylistHierarchy]
     * instance for playlist lookup operations during deserialization and hierarchy updates.
     */
    private inner class MutablePlaylist(
        id: Int,
        isDirectory: Boolean,
        name: String,
        audioItems: List<AudioItem> = listOf(),
        playlists: Set<MutableAudioPlaylist> = setOf()
    ): MutablePlaylistBase(id, isDirectory, name, audioItems, playlists), MutableAudioPlaylist {
        override fun clone(): MutablePlaylist = MutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet())
    }
}

@JvmField
@get:JvmName("playlistSerializerModule")
internal val playlistSerializerModule =
    SerializersModule {
        polymorphic(ReactiveAudioPlaylist::class) {
            subclass(MutableAudioPlaylistSerializer)
        }
    }