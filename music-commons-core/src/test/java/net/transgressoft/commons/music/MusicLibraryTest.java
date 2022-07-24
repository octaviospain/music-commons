package net.transgressoft.commons.music;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.audio.AudioItemRepository;
import net.transgressoft.commons.music.playlist.AudioPlaylist;
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory;
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository;
import net.transgressoft.commons.music.waveform.AudioWaveform;
import net.transgressoft.commons.query.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MusicLibraryTest {

    MusicLibrary<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>> musicLibrary;

    @Mock
    AudioItemRepository<AudioItem> audioItemRepository;
    @Mock
    AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>> audioPlaylistRepository;
    @Mock
    Repository<AudioWaveform> audioWaveformRepository;

    @Test
    @DisplayName("Music api test")
    void musicApiTest() throws Exception {
        musicLibrary = new DefaultMusicLibrary<>(audioItemRepository, audioPlaylistRepository, audioWaveformRepository);

        musicLibrary.audioItems();
        verify(audioItemRepository).iterator();

        musicLibrary.deleteAudioItems(Collections.singleton(mock(AudioItem.class)));
        verify(audioItemRepository).removeAll(any());
        verify(audioPlaylistRepository).removeAudioItems(any());
        verify(audioWaveformRepository).findById(anyInt());

        musicLibrary.addAudioItemsToPlaylist(Collections.emptyList(), mock(AudioPlaylist.class));
        verify(audioPlaylistRepository).addAudioItemsToPlaylist(any(), any());
        musicLibrary.removeAudioItemsFromPlaylist(Collections.emptyList(), mock(AudioPlaylist.class));
        verify(audioPlaylistRepository).removeAudioItemsFromPlaylist(any(), any());
        musicLibrary.movePlaylist(mock(AudioPlaylist.class), mock(AudioPlaylistDirectory.class));
        verify(audioPlaylistRepository).movePlaylist(any(), any());

        when(audioWaveformRepository.findById(anyInt())).thenReturn(Optional.of(mock(AudioWaveform.class)));
        var result = musicLibrary.getOrCreateWaveformAsync(mock(AudioItem.class), (short) 500, (short) 150);
        assertThat(result.get()).isNotNull();

        verifyNoMoreInteractions(audioItemRepository, audioPlaylistRepository, audioWaveformRepository);
    }
}
