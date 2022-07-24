package net.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableList;
import net.transgressoft.commons.music.audio.AudioItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Octavio Calleya
 */
class ImmutableAudioPlaylistTest {

    AudioPlaylist<AudioItem> playlist;

    @Test
    @DisplayName("AudioPlaylist properties")
    void playlistPropertiesTest() {
        playlist = new ImmutableAudioPlaylist("Hits");

        assertEquals("Hits", playlist.name());
        assertTrue(playlist.isEmpty());

        AudioItem item1 = mock(AudioItem.class);
        AudioItem item2 = mock(AudioItem.class);
        AudioItem item3 = mock(AudioItem.class);

        ImmutableList<AudioItem> list = ImmutableList.of(item1, item2, item3);
        playlist = (AudioPlaylist<AudioItem>) playlist.addAudioItems(list).name("Best hits");

        assertFalse(playlist.isEmpty());
        assertEquals(list, playlist.audioItems());
        assertEquals("Best hits", playlist.name());

        playlist = playlist.removeAudioItems(Collections.singleton(item1));
        assertEquals(ImmutableList.of(item2, item3), playlist.audioItems());

        AudioPlaylist<AudioItem> playlist2 = new ImmutableAudioPlaylist("Hits");

        playlist = playlist.clear();
        assertTrue(playlist.isEmpty());

        AudioPlaylist<AudioItem> playlist3 = new ImmutableAudioPlaylist("Hits");
        assertEquals(playlist3, playlist2);

        assertEquals("ImmutableAudioPlaylist{name=Hits}", playlist2.toString());
    }
}
