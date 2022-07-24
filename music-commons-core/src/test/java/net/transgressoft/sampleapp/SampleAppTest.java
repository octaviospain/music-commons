package net.transgressoft.sampleapp;

import net.transgressoft.commons.music.StandardMusicLibraryKt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SampleAppTest {

    @Test
    @DisplayName("Sample app test")
    void sampleAppTest() {
        var musicLibrary = StandardMusicLibraryKt.builder().build();
    }
}
