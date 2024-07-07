package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.data.StandardDataEvent.Type.*
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemManipulationException
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.toIds
import mu.KotlinLogging
import java.io.File
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class AudioPlaylistJsonRepository(name: String, jsonFile: File) : AudioPlaylistRepositoryBase<AudioItem, ReactiveAudioPlaylist>(name, jsonFile, MutableAudioPlaylistSerializer) {

    private val logger = KotlinLogging.logger {}

    constructor(name: String, file: File, audioItemRepository: AudioItemRepository<AudioItem>) : this(name, file) {
        disableEvents(CREATE, UPDATE, DELETE, READ)
        runForAll {
            val playlistWithAudioItems = MutablePlaylist(it.id, it.isDirectory, it.name, mapAudioItemsFromIds(it.audioItems.toIds(), audioItemRepository))
            entitiesById[it.id] = playlistWithAudioItems
        }
        runForAll {
            val playlistMissingPlaylists = entitiesById[it.id]!!
            val foundPlaylists = findDeserializedPlaylistsFromIds(it.playlists.toIds(), entitiesById)
            playlistMissingPlaylists.addPlaylists(foundPlaylists)
        }

        activateEvents(CREATE, UPDATE, DELETE)
    }

    private fun mapAudioItemsFromIds(audioItemIds: List<Int>, audioItemRepository: AudioItemRepository<AudioItem>) =
        audioItemIds.map {
            audioItemRepository.findById(it).orElseThrow { AudioItemManipulationException("AudioItem with id $it not found during deserialization") }
        }.toList()

    private fun findDeserializedPlaylistsFromIds(
        playlists: Set<Int>,
        playlistsById: Map<Int, ReactiveAudioPlaylist>
    ): List<ReactiveAudioPlaylist> =
        playlists.stream().map {
            return@map playlistsById[it] ?: throw AudioItemManipulationException("AudioPlaylist with id $it not found during deserialization")
        }.toList()

    override fun createPlaylist(name: String): ReactiveAudioPlaylist = createPlaylist(name, emptyList())

    override fun createPlaylist(name: String, audioItems: List<AudioItem>): ReactiveAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return MutablePlaylist(newId(), false, name, audioItems).also {
            logger.debug { "Created playlist $it" }
            add(it)
        }
    }

    override fun createPlaylistDirectory(name: String): ReactiveAudioPlaylist = createPlaylistDirectory(name, emptyList())

    override fun createPlaylistDirectory(name: String, audioItems: List<AudioItem>): ReactiveAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return MutablePlaylist(newId(), true, name, audioItems).also {
            logger.debug { "Created playlist directory $it" }
            add(it)
        }
    }

    override fun entityClone(entity: ReactiveAudioPlaylist): ReactiveAudioPlaylist =
        MutablePlaylist(entity.id, entity.isDirectory, entity.name, entity.audioItems, entity.playlists)

    override fun toString() = "PlaylistRepository(name=$name, playlistsCount=${entitiesById.size})"

    private inner class MutablePlaylist(
        id: Int,
        isDirectory: Boolean,
        name: String,
        audioItems: List<AudioItem> = listOf(),
        playlists: Set<ReactiveAudioPlaylist> = setOf()
    ) : MutablePlaylistBase(id, isDirectory, name, audioItems, playlists), ReactiveAudioPlaylist
}

val playlistSerializerModule = SerializersModule {
    polymorphic(MutableAudioPlaylist::class) {
        subclass(MutableAudioPlaylistSerializer)
    }
}
