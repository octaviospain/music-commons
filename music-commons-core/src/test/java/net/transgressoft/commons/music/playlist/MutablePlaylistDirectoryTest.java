package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.MusicLibraryTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;

class MutablePlaylistDirectoryTest extends MusicLibraryTestBase {


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
