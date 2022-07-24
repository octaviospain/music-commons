package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.audio.AudioItemTestFactory;
import net.transgressoft.commons.music.audio.attribute.StringAudioItemAttribute;
import net.transgressoft.commons.query.attribute.EntityAttribute;
import net.transgressoft.commons.query.attribute.UnknownAttributeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import static com.google.common.truth.Truth.assertThat;
import static net.transgressoft.commons.music.playlist.attribute.PlaylistNodeAttribute.ANCESTOR;
import static net.transgressoft.commons.music.playlist.attribute.PlaylistNodeAttribute.SELF;
import static net.transgressoft.commons.music.playlist.attribute.PlaylistStringAttribute.NAME;
import static net.transgressoft.commons.music.playlist.attribute.PlaylistStringAttribute.UNIQUE_ID;
import static org.junit.Assert.assertThrows;

/**
 * @author Octavio Calleya
 */
class PlaylistNodeTest {

    AudioPlaylistDirectory rootNode = AudioPlaylistDirectory.ROOT;
    AudioPlaylistDirectory nullNode = AudioPlaylistDirectory.NULL;
    AudioItemTestFactory audioItemTestFactory = new AudioItemTestFactory();

    @Test
    @DisplayName("Root node and not Root node properties")
    void propertiesTest() {
        var classLoaderHashcode = getClass().getClassLoader().hashCode();
        assertThat(rootNode.id()).isEqualTo(0);
        assertThat(rootNode.isDirectory()).isTrue();
        assertThat(rootNode.getName()).isEqualTo("ROOT-" + classLoaderHashcode);
        assertThat(rootNode.getAncestor()).isEqualTo(nullNode);
        assertThat(rootNode.getUniqueId()).isEqualTo("0-D-ROOT-" + classLoaderHashcode);
        assertThat(rootNode.descendantPlaylistsIterator().hasNext()).isFalse();
        assertThat(rootNode.toString()).isEqualTo("{id=0, name=ROOT-" + classLoaderHashcode + ", ancestor={id=-1}, descendantPlaylists=0, audioItems=0}");

        var p1d = AudioPlaylistDirectory.builder("P1D").build();
        assertThat(p1d.isDirectory()).isTrue();
        assertThat(p1d.getAncestor()).isEqualTo(rootNode);
        assertThat(p1d.getUniqueId()).isEqualTo(p1d.id() + "-D-P1D");

        var p1a = AudioPlaylist.builder("P1")
                .ancestor(p1d)
                .build();

        assertThat(p1a.isDirectory()).isFalse();
        assertThat(p1a.getAncestor()).isEqualTo(p1d);
        assertThat(p1a.getUniqueId()).isEqualTo(p1a.id() + "-P1");
        assertThrows(UnsupportedOperationException.class, () -> p1a.removePlaylist(p1d));

        rootNode.addPlaylist(p1d);
        rootNode.addPlaylist(p1a);
        assertThat(toPlaylistNodeList(rootNode.descendantPlaylistsIterator())).containsExactly(p1d, p1a);

        assertThat(rootNode.getAttribute(NAME)).isEqualTo(rootNode.getName());
        assertThat(rootNode.getAttribute(UNIQUE_ID)).isEqualTo(rootNode.getUniqueId());
        assertThat(rootNode.getAttribute(SELF)).isEqualTo(rootNode);
        assertThat(rootNode.getAttribute(ANCESTOR)).isEqualTo(rootNode.getAncestor());
        assertThrows(UnknownAttributeException.class, () -> rootNode.getAttribute(SomeAttribute.THING));

        assertThrows(UnsupportedOperationException.class, () -> rootNode.setName(""));
        assertThrows(UnsupportedOperationException.class, () -> rootNode.setAncestor(rootNode));
        assertThrows(UnsupportedOperationException.class, () -> rootNode.addAudioItems(null));
        var exception = assertThrows(UnknownAttributeException.class, () -> rootNode.getAttribute(StringAudioItemAttribute.TITLE));
        assertThat(exception.getMessage()).isEqualTo("Unknown attribute TITLE provided for " + AudioPlaylistDirectory.class.getName() + "$2");
    }

    @Test
    @DisplayName("Addition and deletion of nested playlists and audio items")
    void additionAndDeletionOfPlaylistsTest() {
        var audioItems1 = audioItemTestFactory.createTestAudioItemsSet(5);

        var playlist1 = AudioPlaylist.builder("Playlist")
                .audioItems(audioItems1)
                .build();
        assertThat(toAudioItemsList(playlist1.audioItemsListIterator())).containsExactlyElementsIn(audioItems1);
        assertThat(playlist1.isEmptyOfAudioItems()).isFalse();
        assertThat(playlist1.getAncestor()).isEqualTo(rootNode);

        playlist1.setName("Playlist1");
        assertThat(playlist1.getName()).isEqualTo("Playlist1");

        var audioItems2 = audioItemTestFactory.createTestAudioItemsSet(7);
        var playlistDirectory1 = AudioPlaylistDirectory.builder("Playlist Directory")
                .audioItems(audioItems2).build();
        assertThat(toAudioItemsList(playlistDirectory1.audioItemsListIterator())).containsExactlyElementsIn(audioItems2);
        assertThat(playlistDirectory1.isEmptyOfAudioItems()).isFalse();
        assertThat(playlistDirectory1.getAncestor()).isEqualTo(rootNode);
        assertThat(playlistDirectory1.isEmptyOfPlaylists()).isTrue();

        playlistDirectory1.addPlaylist(playlist1);
        assertThat(playlistDirectory1.isEmptyOfPlaylists()).isFalse();
        assertThat(toAudioItemsList(playlistDirectory1.audioItemsListIterator())).containsExactlyElementsIn(audioItems2);
        assertThat(toPlaylistNodeList(playlistDirectory1.descendantPlaylistsIterator())).containsExactly(playlist1);
        assertThat(playlist1.getAncestor()).isEqualTo(playlistDirectory1);

        playlistDirectory1.removeAudioItems(audioItems2.get(5));
        assertThat(toAudioItemsList(playlistDirectory1.audioItemsListIterator())).doesNotContain(audioItems2.get(5));
        audioItems2.remove(5);

        var playlist2 = AudioPlaylist.builder("Playlist2").build();
        assertThat(playlist2.isEmptyOfAudioItems()).isTrue();

        var audioItems3 = audioItemTestFactory.createTestAudioItemsSet(3);
        playlist2.addAudioItems(audioItems3);
        assertThat(toAudioItemsList(playlist2.audioItemsListIterator())).containsExactlyElementsIn(audioItems3);

        playlistDirectory1.addPlaylist(playlist2);
        assertThat(playlist2.getAncestor()).isEqualTo(playlistDirectory1);
        assertThat(toAudioItemsList((playlistDirectory1.audioItemsListIterator()))).containsAtLeastElementsIn(audioItems2);

        playlist1.clearAudioItems();
        assertThat(toAudioItemsList((playlistDirectory1.audioItemsListIterator()))).containsExactlyElementsIn(audioItems2);

        playlist1.addAudioItems(audioItems1);
        assertThat(toAudioItemsList((playlistDirectory1.audioItemsListIterator()))).containsAtLeastElementsIn(audioItems2);

        playlistDirectory1.removePlaylist(playlist1);
        assertThat(toAudioItemsList((playlistDirectory1.audioItemsListIterator()))).containsExactlyElementsIn(audioItems2);
        assertThat(playlist1.getAncestor()).isEqualTo(null);

        playlistDirectory1.clearAudioItemsFromPlaylists();
        assertThat(playlist2.isEmptyOfAudioItems()).isTrue();
        assertThat(playlistDirectory1.isEmptyOfAudioItems()).isFalse();

        playlistDirectory1.clearDescendantPlaylists();
        assertThat(toPlaylistNodeList(playlistDirectory1.descendantPlaylistsIterator())).isEmpty();

        playlistDirectory1.clearAudioItems();
        assertThat(playlistDirectory1.isEmptyOfAudioItems()).isTrue();
    }

    List<PlaylistNode<AudioItem>> toPlaylistNodeList(ListIterator<PlaylistNode<AudioItem>> listIterator) {
        var list = new ArrayList<PlaylistNode<AudioItem>>();
        while (listIterator.hasNext()) {
            list.add(listIterator.next());
        }
        return list;
    }

    List<AudioItem> toAudioItemsList(ListIterator<AudioItem> listIterator) {
        var list = new ArrayList<AudioItem>();
        while (listIterator.hasNext()) {
            list.add(listIterator.next());
        }
        return list;
    }

    private enum SomeAttribute implements EntityAttribute<String> {

        THING;
    }
}
