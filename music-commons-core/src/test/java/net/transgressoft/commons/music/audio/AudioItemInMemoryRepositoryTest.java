package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.music.MusicLibraryTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static com.google.common.truth.Truth.assertThat;

class AudioItemInMemoryRepositoryTest extends MusicLibraryTestBase {

    @Test
    @DisplayName("Create repository and new item")
    void createRepositoryAndNewItemTest() {
        var audioItemRepository = new AudioItemInMemoryRepository<>(createTestAudioItemsSet(10));
        assertThat(audioItemRepository).hasSize(10);

        var audioItem = audioItemRepository.create(Path.of("home", "music"), "New song", Duration.ofSeconds(120), 320).build();
        assertThat(audioItemRepository).hasSize(11);
        assertThat(audioItem.title()).isEqualTo("New song");
    }
}