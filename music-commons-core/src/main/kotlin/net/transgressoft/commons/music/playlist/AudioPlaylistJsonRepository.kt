package net.transgressoft.commons.music.playlist

import kotlinx.serialization.json.Json
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemRepository
import java.io.File

open class AudioPlaylistJsonRepository internal constructor(
    playlistsById: Map<Int, AudioPlaylist<AudioItem>> = emptyMap(),
    jsonFile: File? = null
) : AudioPlaylistJsonRepositoryBase<AudioItem, AudioPlaylist<AudioItem>>(playlistsById, jsonFile) {

    constructor() : this(emptyMap(), null)

    constructor(playlistsById: Map<Int, AudioPlaylist<AudioItem>>) : this(playlistsById, null)

    companion object {
        fun loadFromFile(file: File, audioItemRepository: AudioItemRepository<AudioItem>): AudioPlaylistJsonRepository {
            require(file.exists().and(file.canRead().and(file.canWrite()))) {
                "Provided jsonFile does not exist or is not writable"
            }
            return Json.decodeFromString<List<InternalAudioPlaylist>>(file.readText()).let {
                val initialPlaylists = mapFromSerializablePlaylists(it, audioItemRepository)
                AudioPlaylistJsonRepository(initialPlaylists, file)
            }
        }

        fun initialize(file: File) = AudioPlaylistJsonRepository(emptyMap(),  file)
    }

    final override fun createMutablePlaylist(id: Int, isDirectory: Boolean, name: String, audioItems: List<AudioItem>): MutableAudioPlaylist<AudioItem> =
        MutablePlaylist(id, isDirectory, name, audioItems.toMutableList())

    final override fun toAudioPlaylist(mutableAudioPlaylist: MutableAudioPlaylist<AudioItem>) = mutableAudioPlaylist.toAudioPlaylist()
}