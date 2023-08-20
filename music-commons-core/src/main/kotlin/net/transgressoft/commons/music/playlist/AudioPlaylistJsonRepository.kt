package net.transgressoft.commons.music.playlist

import kotlinx.serialization.json.Json
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemRepository
import java.io.File

class AudioPlaylistJsonRepository(jsonFile: File? = null) : AudioPlaylistJsonRepositoryBase<AudioItem, AudioPlaylist<AudioItem>>(jsonFile) {

    constructor() : this(null)

    constructor(file: File, audioItemRepository: AudioItemRepository<AudioItem>) : this(file) {
        require(file.exists().and(file.canWrite()).and(file.extension == "json")) {
            "Provided jsonFile does not exist, is not writable or is not a json file"
        }
        Json.decodeFromString<List<InternalAudioPlaylist>>(file.readText()).let {
            val initialPlaylists = mapFromSerializablePlaylists(it, audioItemRepository)
            addOrReplaceAll(initialPlaylists.values.toSet())
        }
    }

    override fun createMutablePlaylist(id: Int, isDirectory: Boolean, name: String, audioItems: List<AudioItem>): MutableAudioPlaylist<AudioItem> =
        MutablePlaylist(id, isDirectory, name, audioItems.toMutableList())

    override fun toAudioPlaylist(mutableAudioPlaylist: MutableAudioPlaylist<AudioItem>) = mutableAudioPlaylist.toAudioPlaylist()
}