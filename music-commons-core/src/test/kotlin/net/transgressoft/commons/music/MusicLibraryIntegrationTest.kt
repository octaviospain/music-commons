package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemInMemoryRepository
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistInMemoryRepository
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveformInMemoryRepository
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MusicLibraryIntegrationTest {

    var audioItemRepository: AudioItemRepository<AudioItem> = AudioItemInMemoryRepository()
    var audioWaveformRepository: AudioWaveformRepository<ScalableAudioWaveform> = AudioWaveformInMemoryRepository()
    var audioPlaylistRepository: AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>> =  AudioPlaylistInMemoryRepository()

    @BeforeEach
    fun beforeEach() {
        audioItemRepository.clear()
        audioWaveformRepository.clear()
        audioPlaylistRepository.clear()
    }

    @Test
    fun `Operations from Audio Item repository impact other subscribed repositories`() {
        audioItemRepository.subscribe(audioWaveformRepository.audioItemEventSubscriber)
        audioItemRepository.subscribe(audioPlaylistRepository.audioItemEventSubscriber)
        //TODO
    }
}