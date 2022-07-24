package net.transgressoft.commons.music;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MusicLibraryTest {

    MusicLibrary musicLibrary;

    @Test
    @DisplayName("Music api test")
    void musicApiTest() {
        musicLibrary = new DefaultMusicLibrary();


    }
}
