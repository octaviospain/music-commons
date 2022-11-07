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
}