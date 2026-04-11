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

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import mu.KotlinLogging

/**
 * Default implementation of [PlaylistHierarchy] for managing [MutableAudioPlaylist] instances.
 *
 * Constructs [MutablePlaylist] instances directly as top-level classes. JSON serialization is
 * handled by `lirpSerializer(MutablePlaylist(0))` passed to [net.transgressoft.lirp.persistence.json.JsonFileRepository]
 * at construction time — no companion object serializer is needed.
 *
 * Audio item and nested playlist references are resolved lazily via lirp aggregate delegates
 * registered in [net.transgressoft.lirp.persistence.LirpContext] on construction.
 */
internal class DefaultPlaylistHierarchy(
    repository: Repository<Int, MutableAudioPlaylist> = VolatileRepository()
) : PlaylistHierarchyBase<AudioItem, MutableAudioPlaylist>(repository), PlaylistHierarchy {

    private val logger = KotlinLogging.logger {}

    override val playlistElementType = MutableAudioPlaylist::class

    init {
        RegistryBase.deregisterRepository(MutableAudioPlaylist::class.java)
        RegistryBase.registerRepository(MutableAudioPlaylist::class.java, repository)
    }

    override fun createPlaylist(name: String): MutableAudioPlaylist = createPlaylist(name, emptyList())

    override fun createPlaylist(
        name: String,
        audioItems: List<AudioItem>
    ): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return MutablePlaylist(newId(), name, false, audioItems.map { it.id }).also {
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
        return MutablePlaylist(newId(), name, true, audioItems.map { it.id }).also {
            logger.debug { "Created playlist directory $it" }
            add(it)
        }
    }

    override fun close() {
        super.close()
        RegistryBase.deregisterRepository(MutableAudioPlaylist::class.java)
    }

    override fun toString() = "PlaylistRepository(playlistsCount=${size()})"
}