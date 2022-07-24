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
class MutablePlaylistNodeTest extends MusicLibraryTestBase {

    @Test
    @DisplayName("Mutable audio playlist attributes and operations")
    void mutableAudioPlaylistTest() {
        var playlist1 = new MutablePlaylist<>(1, "Playlist1");

        assertThat(playlist1.id()).isEqualTo(1);
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
    @DisplayName("Mutable audio directory attributes and operations")
    void additionAndDeletionOfPlaylistsTest() {
        var directory1 = new MutablePlaylistDirectory<>(1, "Directory1");
        assertThat(directory1.isDirectory()).isTrue();
        assertThat(directory1.descendantPlaylists()).isEmpty();
        assertThat(directory1.toString()).isEqualTo("MutablePlaylistDirectory{id=1, name=Directory1, descendantPlaylists=0, audioItems=0}");

        var audioItems = createTestAudioItemsSet(5);
        var p1 = new MutablePlaylist<>(10, "p1", audioItems);
        var p2 = new MutablePlaylist<>(11, "p2");
        var d1 = new MutablePlaylistDirectory<>(12, "d1", Collections.singletonList(createTestAudioItem("One")));

        directory1.addPlaylists(p1, p2, d1);
        assertThat(directory1.descendantPlaylists()).hasSize(3);
        assertThat(directory1.containsPlaylist(d1)).isTrue();

        d1.clearAudioItems();
        assertThat(d1.audioItems()).isEmpty();

        var directory2 = new MutablePlaylistDirectory<>(1, "Directory1");
        assertThat(directory1).isEqualTo(directory2);
    }
}
