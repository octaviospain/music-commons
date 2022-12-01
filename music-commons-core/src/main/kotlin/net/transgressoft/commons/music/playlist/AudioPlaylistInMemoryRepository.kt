package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.event.QueryEntityEvent
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.event.AudioItemEventSubscriber

open class AudioPlaylistInMemoryRepository(
    playlistsById: MutableMap<Int, AudioPlaylist<AudioItem>> = HashMap(),
) : AudioPlaylistInMemoryRepositoryBase<AudioItem, AudioPlaylist<AudioItem>>(playlistsById) {

    final override val audioItemEventSubscriber = AudioItemEventSubscriber<AudioItem>().apply {
        addOnNextEventAction(QueryEntityEvent.Type.DELETE) {
            removeAudioItems(it.entities)
        }
    }

    override fun createMutablePlaylist(id: Int, isDirectory: Boolean, name: String, audioItems: List<AudioItem>): MutableAudioPlaylist<AudioItem> =
        MutablePlaylist(id, isDirectory, name, audioItems.toMutableList())

    override fun toAudioPlaylist(mutableAudioPlaylist: MutableAudioPlaylist<AudioItem>) = mutableAudioPlaylist.toAudioPlaylist()
}