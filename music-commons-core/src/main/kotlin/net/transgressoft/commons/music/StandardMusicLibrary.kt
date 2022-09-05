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

typealias AudioRepository = AudioItemRepository<AudioItem>
typealias PlaylistRepository = AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>>
typealias WaveformRepository = AudioWaveformRepository<AudioWaveform>

fun builder(): MusicLibraryBuilder = DefaultMusicLibraryBuilder()

internal class DefaultMusicLibraryBuilder : MusicLibraryBuilder {

    private var audioItemRepository: AudioRepository? = null
    private var audioPlaylistRepository: PlaylistRepository? = null
    private var audioWaveformRepository: WaveformRepository? = null
    private var audioItemEventDispatcher: QueryEventDispatcher<AudioItem>? = null

    override fun withAudioItemRepository(audioItemRepository: AudioRepository) = apply {
        this.audioItemRepository = audioItemRepository
    }

    override fun withPlaylistRepository(audioPlaylistRepository: PlaylistRepository) = apply {
        this.audioPlaylistRepository = audioPlaylistRepository
    }

    override fun withWaveformRepository(audioWaveformRepository: AudioWaveformRepository<AudioWaveform>) = apply {
        this.audioWaveformRepository = audioWaveformRepository
    }

    override fun withQueryEventDispatcher(queryEventDispatcher: QueryEventDispatcher<AudioItem>) = apply {
        this.audioItemEventDispatcher = queryEventDispatcher
    }

    override fun build(): StandardMusicLibrary {
        audioItemEventDispatcher = audioItemEventDispatcher ?: DefaultAudioItemEventDispatcher()

        audioItemRepository = audioItemRepository ?: AudioItemInMemoryRepository(eventDispatcher = audioItemEventDispatcher)
        audioPlaylistRepository = audioPlaylistRepository ?: AudioPlaylistInMemoryRepository()
        audioWaveformRepository = audioWaveformRepository ?: AudioWaveformInMemoryRepository()

        val musicLibrary = DefaultMusicLibrary(audioItemRepository!!, audioPlaylistRepository!!, audioWaveformRepository!!)

        audioItemEventDispatcher!!.subscribe(musicLibrary.audioItemSubscriber)
        return musicLibrary
    }
}

internal class DefaultMusicLibrary(
    audioItemRepository: AudioRepository,
    audioPlaylistRepository: PlaylistRepository,
    audioWaveformRepository: WaveformRepository,
) : MusicLibraryBase<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>, AudioWaveform>(
    audioItemRepository,
    audioPlaylistRepository,
    audioWaveformRepository,
), StandardMusicLibrary