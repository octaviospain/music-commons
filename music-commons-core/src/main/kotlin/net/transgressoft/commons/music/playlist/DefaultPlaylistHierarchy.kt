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

class DefaultPlaylistHierarchy(
    repository: Repository<Int, MutableAudioPlaylist> = VolatileRepository()
): PlaylistHierarchyBase<AudioItem, MutableAudioPlaylist>(repository) {
    private val logger = KotlinLogging.logger {}

    constructor(repository: Repository<Int, MutableAudioPlaylist>, audioLibrary: AudioLibrary<AudioItem>) : this(repository) {
        disableEvents(CREATE, UPDATE, DELETE) // disable events until the initial load from the file is completed
        runForAll {
            val playlistWithAudioItems = MutablePlaylist(it.id, it.isDirectory, it.name, mapAudioItemsFromIds(it.audioItems.toIds(), audioLibrary))
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