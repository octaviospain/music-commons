package net.transgressoft.commons.music

import net.transgressoft.commons.event.QueryEventDispatcher
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemInMemoryRepository
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.audio.DefaultAudioItemEventDispatcher
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory
import net.transgressoft.commons.music.playlist.AudioPlaylistInMemoryRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformInMemoryRepository
import net.transgressoft.commons.music.waveform.AudioWaveformRepository

class StandardMusicLibrary(
    audioItemRepository: AudioItemRepository<AudioItem> = AudioItemInMemoryRepository(),
    audioPlaylistRepository: AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>> = AudioPlaylistInMemoryRepository(),
    audioWaveformRepository: AudioWaveformRepository<AudioWaveform> = AudioWaveformInMemoryRepository(),
    audioItemEventDispatcher: QueryEventDispatcher<AudioItem> = DefaultAudioItemEventDispatcher()
) : MusicLibraryBase<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>, AudioWaveform>(
    audioItemRepository, audioPlaylistRepository, audioWaveformRepository, audioItemEventDispatcher
)