package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemInMemoryRepository
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.event.MusicStats
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistInMemoryRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformInMemoryRepository
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MusicLibraryIntegrationTest {

    lateinit var audioItemRepository: AudioItemRepository<AudioItem>
    lateinit var audioWaveformRepository: AudioWaveformRepository<AudioWaveform>
    lateinit var audioPlaylistRepository: AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>>
    lateinit var musicStats: MusicStats

    @BeforeEach
    fun beforeEach() {
        audioItemRepository = AudioItemInMemoryRepository()
        audioWaveformRepository = AudioWaveformInMemoryRepository()
        audioPlaylistRepository = AudioPlaylistInMemoryRepository()
        musicStats = MusicStats()

        audioItemRepository.subscribe(audioWaveformRepository.audioItemEventSubscriber)
        audioItemRepository.subscribe(audioPlaylistRepository.audioItemEventSubscriber)
        audioItemRepository.subscribe(musicStats)
    }

    @Test
    fun `Operations from Audio Item repository impact other subscribed repositories`() {

    }
}