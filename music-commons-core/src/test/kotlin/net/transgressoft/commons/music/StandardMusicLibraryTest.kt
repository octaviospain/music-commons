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
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyShort
import org.mockito.kotlin.*
import java.util.*

internal class StandardMusicLibraryTest {

    val audioItemRepository: AudioItemRepository<AudioItem> = mock {
        on { iterator() } doReturn Collections.emptyIterator()
    }

    val audioWaveformRepository = mock<AudioWaveformRepository<AudioWaveform>> {
        on { findById(anyInt()) } doReturn Optional.of(mock {})
        on { findById(eq(9)) } doReturn Optional.empty()
        on { create(any<AudioItem>(), anyShort(), anyShort()) } doReturn mock {}
    }

    val audioPlaylistRepository =
        mock<AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>>> {}
    val audioItemEventDispatcher = spy<QueryEventDispatcher<AudioItem>> {}

    val musicLibrary = StandardMusicLibrary(audioItemRepository, audioPlaylistRepository, audioWaveformRepository, audioItemEventDispatcher)

    @Test
    fun `Music api test`() {
        verify(audioItemRepository).iterator()
        verify(audioItemEventDispatcher).subscribe(musicLibrary.audioItemSubscriber)

        musicLibrary.deleteAudioItems(setOf(mock {}))
        verify(audioItemRepository).removeAll(any())
        verify(audioPlaylistRepository).removeAudioItems(any())
        verify(audioWaveformRepository).findById(any())
        verify(audioWaveformRepository).remove(any())
        verifyNoMoreInteractions(audioItemEventDispatcher)

        musicLibrary.addAudioItemsToPlaylist(emptyList(), mock {})
        verify(audioPlaylistRepository).addAudioItemsToPlaylist(any(), any())
        verifyNoMoreInteractions(audioItemEventDispatcher)

        musicLibrary.removeAudioItemsFromPlaylist(emptyList(), mock {})
        verify(audioPlaylistRepository).removeAudioItemsFromPlaylist(any(), any())
        verifyNoMoreInteractions(audioItemEventDispatcher)

        musicLibrary.movePlaylist(mock {}, mock {})
        verify(audioPlaylistRepository).movePlaylist(any(), any())

        val result = musicLibrary.getOrCreateWaveformAsync(mock { on { id } doReturn 9 }, 500.toShort(), 150.toShort())
        assertThat(result.get()).isNotNull()
        verify(audioWaveformRepository).findById(eq(9))
        verify(audioWaveformRepository).create(any(), eq(500), eq(150))

        verifyNoMoreInteractions(audioItemRepository, audioPlaylistRepository, audioWaveformRepository, audioItemEventDispatcher)
    }
}