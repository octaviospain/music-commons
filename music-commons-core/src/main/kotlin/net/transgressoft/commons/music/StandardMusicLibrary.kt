package net.transgressoft.commons.music

import net.transgressoft.commons.event.QueryEventDispatcher
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemInMemoryRepository
import net.transgressoft.commons.music.audio.DefaultAudioItemEventDispatcher
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory
import net.transgressoft.commons.music.playlist.AudioPlaylistInMemoryRepository
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformInMemoryRepository

typealias StandardMusicLibraryBase = MusicLibraryBase<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>, AudioWaveform>

object StandardMusicLibrary : StandardMusicLibraryBase(
    audioItemRepository = AudioItemInMemoryRepository(),
    audioPlaylistRepository = AudioPlaylistInMemoryRepository(),
    audioWaveformRepository = AudioWaveformInMemoryRepository(),
) {
    init {
        val audioItemEventDispatcher: QueryEventDispatcher<AudioItem> = DefaultAudioItemEventDispatcher()
        audioItemEventDispatcher.subscribe(audioItemSubscriber)
    }
}
