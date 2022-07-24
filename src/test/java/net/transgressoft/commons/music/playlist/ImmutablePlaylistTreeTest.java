package net.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.transgressoft.commons.music.audio.AudioItem;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Octavio Calleya
 */
class ImmutablePlaylistTreeTest {

    @Test
    @DisplayName("Playlist Tree operations")
    void propertiesTest() {
        AudioItem item1 = mock(AudioItem.class, "item1");
        AudioItem item2 = mock(AudioItem.class, "item2");
        AudioPlaylist<AudioItem> fiftiesHits = new ImmutableAudioPlaylist("50s hits", ImmutableList.of(item1, item2));
        assertEquals("50s hits", fiftiesHits.name());
        assertEquals(ImmutableList.of(item1, item2), fiftiesHits.audioItems());

        AudioItem item3 = mock(AudioItem.class, "item3");
        AudioItem item4 = mock(AudioItem.class, "item4");
        AudioPlaylist<AudioItem> sixtiesHits = new ImmutableAudioPlaylist("60s hits", ImmutableList.of(item3, item4));
        assertEquals("60s hits", sixtiesHits.name());
        assertEquals(ImmutableList.of(item3, item4), sixtiesHits.audioItems());

        PlaylistTree<AudioItem> bestHits =
                new ImmutablePlaylistTree("Best hits", Collections.emptySet(), ImmutableSet.of(fiftiesHits, sixtiesHits));
        assertEquals("Best hits", bestHits.name());
        assertEquals(ImmutableSet.of(fiftiesHits, sixtiesHits), bestHits.audioPlaylists());
        assertEquals(ImmutableSet.of(item1, item2, item3, item4), bestHits.audioItems());
        assertEquals(Optional.of(bestHits), bestHits.findParentPlaylist("50s hits"));
        assertEquals(Optional.of(bestHits), bestHits.findParentPlaylist("60s hits"));

        AudioItem item5 = mock(AudioItem.class, "item5");
        AudioItem item6 = mock(AudioItem.class, "item6");
        AudioPlaylist<AudioItem> sleepyTracks = new ImmutableAudioPlaylist("Sleepy tracks", ImmutableList.of(item5, item6));
        PlaylistTree<AudioItem> allPlaylists =
                new ImmutablePlaylistTree("All playlists", ImmutableSet.of(bestHits), ImmutableSet.of(sleepyTracks));

        assertEquals("Sleepy tracks", sleepyTracks.name());
        assertEquals(ImmutableList.of(item5, item6), sleepyTracks.audioItems());
        assertEquals(Optional.empty(), allPlaylists.findParentPlaylist("New 1"));
        assertEquals("All playlists", allPlaylists.name());
        assertEquals(ImmutableSet.of(bestHits), allPlaylists.includedPlaylistTrees());
        assertEquals(ImmutableSet.of(sleepyTracks), allPlaylists.audioPlaylists());
        assertEquals(ImmutableSet.of(item1, item2, item3, item4, item5, item6), allPlaylists.audioItems());
        assertEquals(Optional.of(bestHits), allPlaylists.findParentPlaylist("50s hits"));
        assertEquals(Optional.of(bestHits), allPlaylists.findParentPlaylist("60s hits"));
        assertEquals(Optional.of(allPlaylists), allPlaylists.findParentPlaylist("Best hits"));
        assertEquals(Optional.of(allPlaylists), allPlaylists.findParentPlaylist("Sleepy tracks"));

        AudioItem item7 = mock(AudioItem.class, "item7");
        AudioItem item8 = mock(AudioItem.class, "item8");
        AudioPlaylist<AudioItem> newPlaylist = new ImmutableAudioPlaylist("New 1", ImmutableList.of(item7, item8));
        allPlaylists = allPlaylists.addPlaylist(newPlaylist);
        PlaylistTree<AudioItem> addPlaylists2 = allPlaylists.addPlaylist(newPlaylist);
        assertEquals(allPlaylists, addPlaylists2);
        assertEquals(ImmutableSet.of(sleepyTracks, newPlaylist), allPlaylists.audioPlaylists());
        assertEquals(ImmutableSet.of(bestHits), allPlaylists.includedPlaylistTrees());
        assertEquals(ImmutableSet.of(item1, item2, item3, item4, item5, item6, item7, item8), allPlaylists.audioItems());
        assertEquals(Optional.of(allPlaylists), allPlaylists.findParentPlaylist("New 1"));

        AudioItem item9 = mock(AudioItem.class, "item9");
        AudioItem item10 = mock(AudioItem.class, "item10");
        AudioPlaylist<AudioItem> newTreePlaylist = new ImmutableAudioPlaylist("New Tree Playlist", ImmutableList.of(item9, item10));
        PlaylistTree<AudioItem> newTree = new ImmutablePlaylistTree("New Tree", Collections.emptySet(), ImmutableSet.of(newTreePlaylist));

        allPlaylists = allPlaylists.addPlaylistTree(newTree);
        assertEquals(ImmutableSet.of(bestHits, newTree), allPlaylists.includedPlaylistTrees());
        assertEquals(ImmutableSet.of(item1, item2, item3, item4, item5, item6, item7, item8, item9, item10), allPlaylists.audioItems());
        assertEquals(Optional.of(allPlaylists), allPlaylists.findParentPlaylist("New Tree"));

        allPlaylists = allPlaylists.addPlaylistTree(bestHits);
        assertEquals(ImmutableSet.of(item5, item6, item7, item8, item9, item10), allPlaylists.audioItems());
        assertEquals(Optional.empty(), allPlaylists.findParentPlaylist("Best hits"));
        assertEquals(Optional.empty(), allPlaylists.findParentPlaylist("50s hits"));
        assertEquals(Optional.empty(), allPlaylists.findParentPlaylist("60s hits"));

        allPlaylists = allPlaylists.removeAudioPlaylist(newPlaylist);
        assertEquals(ImmutableSet.of(item5, item6, item9, item10), allPlaylists.audioItems());
        assertEquals(Optional.empty(), allPlaylists.findParentPlaylist("New 1"));

        assertFalse(allPlaylists.removeAudioItems(ImmutableSet.of(item4)));
        assertTrue(allPlaylists.removeAudioItems(ImmutableSet.of(item5)));
        assertEquals(ImmutableSet.of(item6, item9, item10), allPlaylists.audioItems());

        allPlaylists.clearIncludedPlaylistTrees();
        assertTrue(allPlaylists.includedPlaylistTrees().isEmpty());
        assertFalse(allPlaylists.audioItems().isEmpty());

        allPlaylists.clearPlaylists();
        assertTrue(allPlaylists.audioPlaylists().isEmpty());
        assertTrue(allPlaylists.audioItems().isEmpty());
    }

    @Test
    @DisplayName("Addition and deletion of nested playlists")
    void additionAndDeletionOfPlaylistsTest() {
        AudioPlaylist<AudioItem> rock = new ImmutableAudioPlaylist("Rock");
        AudioPlaylist<AudioItem> pop = new ImmutableAudioPlaylist("Pop");
        PlaylistTree<AudioItem> fifties = new ImmutablePlaylistTree("50s", Collections.emptySet(), ImmutableSet.of(rock, pop));
        assertEquals(Optional.of(rock), fifties.findAudioPlaylistByName("Rock"));
        assertEquals(Optional.of(pop), fifties.findAudioPlaylistByName("Pop"));

        AudioPlaylist<AudioItem> sixtiesFavs = new ImmutableAudioPlaylist("60s favorites");
        PlaylistTree<AudioItem> bestHits = new ImmutablePlaylistTree("Best hits", ImmutableSet.of(fifties), ImmutableSet.of(sixtiesFavs));
        assertEquals(Optional.of(sixtiesFavs), bestHits.findAudioPlaylistByName("60s favorites"));

        PlaylistTree<AudioItem> selection = new ImmutablePlaylistTree("Selection");
        AudioPlaylist<AudioItem> weekFavs = new ImmutableAudioPlaylist("This weeks' favorites");

        PlaylistTree<AudioItem> rootPlaylistTree = new ImmutablePlaylistTree("ROOT", ImmutableSet.of(bestHits, selection), ImmutableSet.of(weekFavs));

        assertEquals(Optional.of(rock), rootPlaylistTree.findAudioPlaylistByName("Rock"));
        assertEquals(Optional.of(bestHits), rootPlaylistTree.findPlaylistTreeByName("Best hits"));
        assertEquals(Optional.of(weekFavs), rootPlaylistTree.findAudioPlaylistByName("This weeks' favorites"));
        assertEquals(Optional.of(selection), rootPlaylistTree.findPlaylistTreeByName("Selection"));
    }

    @Test
    @DisplayName("Move playlists")
    @Disabled
    void movePlaylists() {
        // ROOT
        // ├──Best hits
        // │  ├──50s
        // │  │  ├──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // ├──Selection
        // └──This weeks' favorites

        //        rootPlaylistTree.movePlaylist(rock, selection);
        //
        //        assertFalse(fifties.audioPlaylists().contains(rock));
        //        assertEquals(Optional.of(selection), rootPlaylistTree.findParentPlaylist("Rock"));
        //        assertTrue(selection.audioPlaylists().contains(rock));
        //
        //        rootPlaylistTree.movePlaylistTree(selection, fifties);
        //
        //        assertFalse(rootPlaylistTree.subPlaylistTrees().contains(selection));
        //        assertEquals(Optional.of(fifties), rootPlaylistTree.findParentPlaylist("Selection"));
        //        assertTrue(fifties.subPlaylistTrees().contains(selection));
    }

    @Test
    @DisplayName("Compare and equals")
    void compareAndEqualsTest() {
        AudioItem item1 = mock(AudioItem.class, "item1");
        AudioItem item2 = mock(AudioItem.class, "item2");
        AudioPlaylist<AudioItem> sixtiesHits = new ImmutableAudioPlaylist("60s hits", ImmutableList.of(item1, item2));

        PlaylistItem<AudioItem> bestHits =
                new ImmutablePlaylistTree("Best hits", Collections.emptySet(), ImmutableSet.of(sixtiesHits));
        assertEquals("Best hits", bestHits.name());
        bestHits = bestHits.name("Best hits of my life");
        assertEquals("Best hits of my life", bestHits.name());

        AudioItem item3 = mock(AudioItem.class, "item3");
        AudioItem item4 = mock(AudioItem.class, "item4");
        AudioPlaylist<AudioItem> fiftiesHits = new ImmutableAudioPlaylist("50s hits", ImmutableList.of(item3, item4));

        PlaylistItem<AudioItem> bestHits2 =
                new ImmutablePlaylistTree("Best hits", Collections.emptySet(), ImmutableSet.of(fiftiesHits));

        assertFalse(bestHits.equals(bestHits2));

        bestHits2 = bestHits2.name("est hits of my life");

        bestHits2.removeAudioItems(ImmutableSet.of(item3, item4));
        fiftiesHits.name("60s hits");
        fiftiesHits.addAudioItems(ImmutableList.of(item1, item2));

        bestHits2 = bestHits2.name("Best hits of my life");
        assertTrue(bestHits.equals(bestHits2));
        assertEquals("ImmutablePlaylistTree{audioPlaylists=[ImmutableAudioPlaylist{name=60s hits}], includedPlaylistTrees=[], name=Best hits of my life}", bestHits.toString());
    }
}
