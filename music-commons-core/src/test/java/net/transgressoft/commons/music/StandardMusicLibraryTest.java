package net.transgressoft.commons.music;

import net.transgressoft.commons.event.QueryEventDispatcher;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.audio.AudioItemRepository;
import net.transgressoft.commons.music.playlist.AudioPlaylist;
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory;
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository;
import net.transgressoft.commons.music.waveform.AudioWaveform;
import net.transgressoft.commons.music.waveform.AudioWaveformRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StandardMusicLibraryTest {

    MusicLibrary<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>, AudioWaveform> musicLibrary;

    @Mock
    AudioItemRepository<AudioItem> audioItemRepository;
    @Mock
    AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>> audioPlaylistRepository;
    @Mock
    AudioWaveformRepository<AudioWaveform> audioWaveformRepository;
    @Spy
    QueryEventDispatcher<AudioItem> audioItemEventDispatcher;

    @Test
    @DisplayName("Music api test")
    void musicApiTest() throws Exception {
        when(audioItemRepository.iterator()).thenReturn(Collections.emptyIterator());

        musicLibrary = StandardMusicLibrary.INSTANCE;

        verify(audioItemRepository).iterator();
        verify(audioItemEventDispatcher).subscribe(musicLibrary.getAudioItemSubscriber());

        musicLibrary.deleteAudioItems(Collections.singleton(mock(AudioItem.class)));
        verify(audioItemRepository).removeAll(any());
        verify(audioPlaylistRepository).removeAudioItems(any());
        verify(audioWaveformRepository).findById(anyInt());
        verifyNoMoreInteractions(audioItemEventDispatcher);

        musicLibrary.addAudioItemsToPlaylist(Collections.emptyList(), mock(AudioPlaylist.class));
        verify(audioPlaylistRepository).addAudioItemsToPlaylist(any(), any());
        verifyNoMoreInteractions(audioItemEventDispatcher);

        musicLibrary.removeAudioItemsFromPlaylist(Collections.emptyList(), mock(AudioPlaylist.class));
        verify(audioPlaylistRepository).removeAudioItemsFromPlaylist(any(), any());
        verifyNoMoreInteractions(audioItemEventDispatcher);

        musicLibrary.movePlaylist(mock(AudioPlaylist.class), mock(AudioPlaylistDirectory.class));
        verify(audioPlaylistRepository).movePlaylist(any(), any());

        when(audioWaveformRepository.findById(anyInt())).thenReturn(Optional.of(mock(AudioWaveform.class)));
        var result = musicLibrary.getOrCreateWaveformAsync(mock(AudioItem.class), (short) 500, (short) 150);
        assertThat(result.get()).isNotNull();

        verifyNoMoreInteractions(audioItemRepository, audioPlaylistRepository, audioWaveformRepository, audioItemEventDispatcher);
    }
}
