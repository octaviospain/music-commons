package net.transgressoft.commons.music.playlist;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Octavio Calleya
 */
class ImmutableAudioPlaylistRepositoryTest {

    @Test
    @DisplayName("Addition and deletion of nested playlists")
    void additionAndDeletionOfPlaylistsTest() {
        SimpleAudioPlaylistRepository audioPlaylistRepository = new SimpleAudioPlaylistRepository();
        assertEquals(new ImmutablePlaylistTree("ROOT_PLAYLIST"), audioPlaylistRepository.getRootPlaylistTree());
    }
}
