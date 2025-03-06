package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.data.StandardCrudEvent.Type.CREATE
import net.transgressoft.commons.data.StandardCrudEvent.Type.DELETE
import net.transgressoft.commons.data.StandardCrudEvent.Type.UPDATE
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemManipulationException
import net.transgressoft.commons.music.audio.AudioRepository
import net.transgressoft.commons.toIds
import mu.KotlinLogging
import java.io.File
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

typealias PlaylistRepository = AudioPlaylistRepository<AudioItem, MutableAudioPlaylist>

class AudioPlaylistJsonRepository(
    name: String,
    jsonFile: File
): AudioPlaylistRepositoryBase<AudioItem, MutableAudioPlaylist>(name, jsonFile, MutableAudioPlaylistSerializer) {
    private val logger = KotlinLogging.logger {}

    constructor(name: String, file: File, audioItemRepository: AudioRepository) : this(name, file) {
        disableEvents(CREATE, UPDATE, DELETE) // disable events until initial load from file is completed
        runForAll {
            val playlistWithAudioItems = MutablePlaylist(it.id, it.isDirectory, it.name, mapAudioItemsFromIds(it.audioItems.toIds(), audioItemRepository))
            entitiesById[it.id] = playlistWithAudioItems
        }
        runForAll {
            val playlistMissingPlaylists = entitiesById[it.id] ?: throw IllegalStateException("Playlist ID ${it.id} not found after initial processing")
            val foundPlaylists = findDeserializedPlaylistsFromIds(it.playlists.toIds(), entitiesById)
            playlistMissingPlaylists.addPlaylists(foundPlaylists)
        }

        activateEvents(CREATE, UPDATE, DELETE)
    }

    private fun mapAudioItemsFromIds(
        audioItemIds: List<Int>,
        audioItemRepository: AudioRepository
    ) = audioItemIds.map {
        audioItemRepository.findById(
            it
        ).orElseThrow { AudioItemManipulationException("AudioItem with id $it not found during deserialization") }
    }.toList()

    private fun findDeserializedPlaylistsFromIds(
        playlists: Set<Int>,
        playlistsById: Map<Int, MutableAudioPlaylist>
    ): List<MutableAudioPlaylist> =
        playlists.stream().map {
            return@map playlistsById[it] ?: throw AudioItemManipulationException("AudioPlaylist with id $it not found during deserialization")
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

    override fun toString() = "PlaylistRepository(name=$name, playlistsCount=${entitiesById.size})"

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

val playlistSerializerModule =
    SerializersModule {
        polymorphic(ReactiveAudioPlaylist::class) {
            subclass(MutableAudioPlaylistSerializer)
        }
    }