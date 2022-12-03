package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

open class AudioPlaylistInMemoryRepository(
    playlistsById: MutableMap<Int, AudioPlaylist<AudioItem>> = HashMap(),
) : AudioPlaylistInMemoryRepositoryBase<AudioItem, AudioPlaylist<AudioItem>>(playlistsById) {

    final override fun createMutablePlaylist(id: Int, isDirectory: Boolean, name: String, audioItems: List<AudioItem>): MutableAudioPlaylist<AudioItem> =
        MutablePlaylist(id, isDirectory, name, audioItems.toMutableList())

    final override fun toAudioPlaylist(mutableAudioPlaylist: MutableAudioPlaylist<AudioItem>) = mutableAudioPlaylist.toAudioPlaylist()
}