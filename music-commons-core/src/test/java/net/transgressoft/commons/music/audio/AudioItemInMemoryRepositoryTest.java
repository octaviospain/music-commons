package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.music.MusicLibraryTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudioItemInMemoryRepositoryTest extends MusicLibraryTestBase {

    @Test
    @DisplayName("Create repository and new item")
    void createRepositoryAndNewItemTest() {
        var audioItemRepository = new AudioItemInMemoryRepository(createTestAudioItemsMap(10));
        assertThat(audioItemRepository).hasSize(10);

        var audioItem = audioItemRepository.create(Path.of("home", "music"), "New song", Duration.ofSeconds(120), 320).build();
        assertThat(audioItemRepository).hasSize(11);
        assertThat(audioItem.title()).isEqualTo("New song");
    }

    @Test
    @DisplayName("Contains AudioItem with artist")
    void containsAudioItemWithArtistTest() {
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>());

        var audioItem = mock(AudioItem.class);
        when(audioItem.artistsInvolved()).thenReturn(Set.of("Michael Jackson", "Moby"));

        assertThat(audioItemRepository.containsAudioItemWithArtist("Moby")).isTrue();
    }
}