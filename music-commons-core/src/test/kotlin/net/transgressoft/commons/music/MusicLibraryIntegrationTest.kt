package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemInMemoryRepository
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.event.MusicStats
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistInMemoryRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveformBase
import net.transgressoft.commons.music.waveform.AudioWaveformInMemoryRepository
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MusicLibraryIntegrationTest {

    var audioItemRepository: AudioItemRepository<AudioItem> = AudioItemInMemoryRepository()
    var audioWaveformRepository: AudioWaveformRepository<AudioWaveformBase> = AudioWaveformInMemoryRepository()
    var audioPlaylistRepository: AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>> =  AudioPlaylistInMemoryRepository()
    var musicStats: MusicStats = MusicStats()

    @BeforeEach
    fun beforeEach() {
        audioItemRepository.clear()
        audioWaveformRepository.clear()
        audioPlaylistRepository.clear()
        // verify or reset MusicStats
    }

    @Test
    fun `Operations from Audio Item repository impact other subscribed repositories`() {
        audioItemRepository.subscribe(audioWaveformRepository.audioItemEventSubscriber)
        audioItemRepository.subscribe(audioPlaylistRepository.audioItemEventSubscriber)
        audioItemRepository.subscribe(musicStats)

    }
}