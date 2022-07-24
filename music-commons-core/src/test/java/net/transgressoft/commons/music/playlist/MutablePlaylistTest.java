package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.MusicLibraryTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static net.transgressoft.commons.music.audio.StringAudioItemAttribute.TITLE;

/**
 * @author Octavio Calleya
 */
class MutablePlaylistTest extends MusicLibraryTestBase {

    @Test
    @DisplayName("Mutable audio playlist attributes and operations")
    void mutableAudioPlaylistTest() {
        var playlist1 = new MutablePlaylist<>(1, "Playlist1");

        assertThat(playlist1.getId()).isEqualTo(1);
        assertThat(playlist1.isDirectory()).isFalse();
        assertThat(playlist1.getName()).isEqualTo("Playlist1");
        assertThat(playlist1.getUniqueId()).isEqualTo("1-Playlist1");
        assertThat(playlist1.audioItems()).isEmpty();
        assertThat(playlist1.toString()).isEqualTo("MutablePlaylist{id=1, name=Playlist1, audioItems=0}");

        playlist1.setName("Modified playlist1");
        assertThat(playlist1.getName()).isEqualTo("Modified playlist1");
        assertThat(playlist1.getUniqueId()).isEqualTo("1-Modified playlist1");
        assertThat(playlist1.getAttribute(PlaylistStringAttribute.UNIQUE_ID)).isEqualTo("1-Modified playlist1");

        var audioItems = createTestAudioItemsSet(4);
        playlist1.addAudioItems(audioItems);

        assertThat(playlist1.audioItems()).hasSize(4);
        assertThat(playlist1.audioItemsAllMatch(TITLE.equalsTo("Song title"))).isFalse();

        var customAudioItem = createTestAudioItem("Song title");

        playlist1.addAudioItems(List.of(customAudioItem));
        assertThat(playlist1.audioItems()).hasSize(5);
        assertThat(playlist1.audioItemsAnyMatch(TITLE.equalsTo("Song title"))).isTrue();

        playlist1.removeAudioItems(audioItems);
        assertThat(playlist1.audioItems()).hasSize(1);
        assertThat(playlist1.audioItemsAllMatch(TITLE.equalsTo("Song title"))).isTrue();

        var playlist2 = new MutablePlaylist<>(1, "Modified playlist1", Collections.emptyList());
        assertThat(playlist1).isEqualTo(playlist2);
        assertThat(playlist1).isEquivalentAccordingToCompareTo(playlist2);
        playlist1.clearAudioItems();
    }

    @Test
    @DisplayName("Export to M3u file")
    void exportToM3uFileTest() {

    }
}
