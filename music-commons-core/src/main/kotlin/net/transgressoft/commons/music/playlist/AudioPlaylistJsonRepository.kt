package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.data.StandardDataEvent.Type.*
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemManipulationException
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.toIds
import java.io.File
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class AudioPlaylistJsonRepository(name: String, jsonFile: File) : AudioPlaylistRepositoryBase<AudioItem>(name, jsonFile, MutableAudioPlaylistSerializer) {

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
        playlistsById: Map<Int, MutableAudioPlaylist<AudioItem>>
    ): List<MutableAudioPlaylist<AudioItem>> =
        playlists.stream().map {
            return@map playlistsById[it] ?: throw AudioItemManipulationException("AudioPlaylist with id $it not found during deserialization")
        }.toList()

    override fun toString() = "PlaylistRepository(name=$name, playlistsCount=${entitiesById.size})"
}

val playlistSerializerModule = SerializersModule {
    polymorphic(MutableAudioPlaylist::class) {
        subclass(MutableAudioPlaylistSerializer)
    }
}
