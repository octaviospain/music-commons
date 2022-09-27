package net.transgressoft.commons.music

import com.google.common.truth.Truth.assertThat
import net.transgressoft.commons.event.QueryEventDispatcher
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import java.util.*

internal class StandardMusicLibraryTest {

    val musicLibrary = StandardMusicLibrary

    val audioItemRepository: AudioItemRepository<AudioItem> = mock {
        on { iterator() } doReturn Collections.emptyIterator()
    }

    val audioWaveformRepository = mock<AudioWaveformRepository<AudioWaveform>> {
        on { findById(anyInt()) } doReturn Optional.of(mock {})
    }
    val audioPlaylistRepository = mock<AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>>> {}
    val audioItemEventDispatcher = spy<QueryEventDispatcher<AudioItem>> {}

    @Test
    fun `Music api test`() {
        verify(audioItemRepository).iterator()
        verify(audioItemEventDispatcher).subscribe(musicLibrary.audioItemSubscriber)

        musicLibrary.deleteAudioItems(setOf(mock {}))
        verify(audioItemRepository).removeAll(any())
        verify(audioPlaylistRepository).removeAudioItems(any())
        verify(audioWaveformRepository).findById(anyInt())
        verifyNoMoreInteractions(audioItemEventDispatcher)

        musicLibrary.addAudioItemsToPlaylist(emptyList(), mock {})
        verify(audioPlaylistRepository).addAudioItemsToPlaylist(any(), any())
        verifyNoMoreInteractions(audioItemEventDispatcher)

        musicLibrary.removeAudioItemsFromPlaylist(emptyList(), mock {})
        verify(audioPlaylistRepository).removeAudioItemsFromPlaylist(any(), any())
        verifyNoMoreInteractions(audioItemEventDispatcher)

        musicLibrary.movePlaylist(mock {}, mock {})
        verify(audioPlaylistRepository).movePlaylist(any(), any())

        val result = musicLibrary.getOrCreateWaveformAsync(mock { } , 500.toShort(), 150.toShort())
        assertThat(result.get()).isNotNull()

        verifyNoMoreInteractions(audioItemRepository, audioPlaylistRepository, audioWaveformRepository, audioItemEventDispatcher)
    }
}