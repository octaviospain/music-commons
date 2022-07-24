package net.transgressoft.commons.music.playlist;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Octavio Calleya
 */
class SimpleAudioPlaylistRepositoryTest {

    @Test
    @DisplayName("Addition and deletion of nested playlists")
    void additionAndDeletionOfPlaylistsTest() {
        SimpleAudioPlaylistRepository audioPlaylistRepository = new SimpleAudioPlaylistRepository();
        assertEquals(new SimplePlaylistTree("ROOT_PLAYLIST"), audioPlaylistRepository.getRootPlaylistTree());
    }
}
