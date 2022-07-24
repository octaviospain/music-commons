package net.transgressoft.commons.music

import net.transgressoft.commons.event.QueryEventDispatcher
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository

typealias AudioRepository = AudioItemRepository<AudioItem>
typealias PlaylistRepository = AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>>
typealias WaveformRepository = AudioWaveformRepository<AudioWaveform>

interface MusicLibraryBuilder {
    fun build(): StandardMusicLibrary
    fun withAudioItemRepository(audioItemRepository: AudioRepository): MusicLibraryBuilder
    fun withPlaylistRepository(audioPlaylistRepository: PlaylistRepository): MusicLibraryBuilder
    fun withWaveformRepository(audioWaveformRepository: WaveformRepository): MusicLibraryBuilder
    fun withQueryEventDispatcher(queryEventDispatcher: QueryEventDispatcher<AudioItem>): MusicLibraryBuilder
}