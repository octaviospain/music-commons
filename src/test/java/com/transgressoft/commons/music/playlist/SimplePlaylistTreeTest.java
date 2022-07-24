package com.transgressoft.commons.music.playlist;

import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * @author Octavio Calleya
 */
class SimplePlaylistTreeTest {

    @Test
    @DisplayName("Playlist Tree operations")
    void propertiesTest() {
        AudioItem item1 = mock(AudioItem.class, "item1");
        AudioItem item2 = mock(AudioItem.class, "item2");
        AudioPlaylist<AudioItem> fiftiesHits = new SimpleAudioPlaylist("50s hits", ImmutableList.of(item1, item2));
        assertEquals("50s hits", fiftiesHits.name());
        assertEquals(ImmutableList.of(item1, item2), fiftiesHits.audioItems());

        AudioItem item3 = mock(AudioItem.class, "item3");
        AudioItem item4 = mock(AudioItem.class, "item4");
        AudioPlaylist<AudioItem> sixtiesHits = new SimpleAudioPlaylist("60s hits", ImmutableList.of(item3, item4));
        assertEquals("60s hits", sixtiesHits.name());
        assertEquals(ImmutableList.of(item3, item4), sixtiesHits.audioItems());

        PlaylistTree<AudioItem> bestHits =
                new SimplePlaylistTree("Best hits", Collections.emptySet(), ImmutableSet.of(fiftiesHits, sixtiesHits));
        assertEquals("Best hits", bestHits.name());
        assertEquals(ImmutableSet.of(fiftiesHits, sixtiesHits), bestHits.audioPlaylists());
        assertEquals(ImmutableSet.of(item1, item2, item3, item4), bestHits.audioItems());
        assertEquals(Optional.of(bestHits), bestHits.findParentPlaylist("50s hits"));
        assertEquals(Optional.of(bestHits), bestHits.findParentPlaylist("60s hits"));

        AudioItem item5 = mock(AudioItem.class, "item5");
        AudioItem item6 = mock(AudioItem.class, "item6");
        AudioPlaylist<AudioItem> sleepyTracks = new SimpleAudioPlaylist("Sleepy tracks", ImmutableList.of(item5, item6));
        PlaylistTree<AudioItem> allPlaylists =
                new SimplePlaylistTree("All playlists", ImmutableSet.of(bestHits), ImmutableSet.of(sleepyTracks));

        assertEquals("Sleepy tracks", sleepyTracks.name());
        assertEquals(ImmutableList.of(item5, item6), sleepyTracks.audioItems());
        assertEquals(Optional.empty(), allPlaylists.findParentPlaylist("New 1"));
        assertEquals("All playlists", allPlaylists.name());
        assertEquals(ImmutableSet.of(bestHits), allPlaylists.subPlaylistTrees());
        assertEquals(ImmutableSet.of(sleepyTracks), allPlaylists.audioPlaylists());
        assertEquals(ImmutableSet.of(item1, item2, item3, item4, item5, item6), allPlaylists.audioItems());
        assertEquals(Optional.of(bestHits), allPlaylists.findParentPlaylist("50s hits"));
        assertEquals(Optional.of(bestHits), allPlaylists.findParentPlaylist("60s hits"));
        assertEquals(Optional.of(allPlaylists), allPlaylists.findParentPlaylist("Best hits"));
        assertEquals(Optional.of(allPlaylists), allPlaylists.findParentPlaylist("Sleepy tracks"));

        AudioItem item7 = mock(AudioItem.class, "item7");
        AudioItem item8 = mock(AudioItem.class, "item8");
        AudioPlaylist<AudioItem> newPlaylist = new SimpleAudioPlaylist("New 1", ImmutableList.of(item7, item8));
        assertTrue(allPlaylists.addPlaylist(newPlaylist));
        assertFalse(allPlaylists.addPlaylist(newPlaylist));
        assertEquals(ImmutableSet.of(sleepyTracks, newPlaylist), allPlaylists.audioPlaylists());
        assertEquals(ImmutableSet.of(bestHits), allPlaylists.subPlaylistTrees());
        assertEquals(ImmutableSet.of(item1, item2, item3, item4, item5, item6, item7, item8), allPlaylists.audioItems());
        assertEquals(Optional.of(allPlaylists), allPlaylists.findParentPlaylist("New 1"));

        AudioItem item9 = mock(AudioItem.class, "item9");
        AudioItem item10 = mock(AudioItem.class, "item10");
        AudioPlaylist<AudioItem> newTreePlaylist = new SimpleAudioPlaylist("New Tree Playlist", ImmutableList.of(item9, item10));
        PlaylistTree<AudioItem> newTree = new SimplePlaylistTree("New Tree", Collections.emptySet(), ImmutableSet.of(newTreePlaylist));

        assertTrue(allPlaylists.addPlaylistTree(newTree));
        assertEquals(ImmutableSet.of(bestHits, newTree), allPlaylists.subPlaylistTrees());
        assertEquals(ImmutableSet.of(item1, item2, item3, item4, item5, item6, item7, item8, item9, item10), allPlaylists.audioItems());
        assertEquals(Optional.of(allPlaylists), allPlaylists.findParentPlaylist("New Tree"));

        assertTrue(allPlaylists.removePlaylistTree(bestHits));
        assertEquals(ImmutableSet.of(item5, item6, item7, item8, item9, item10), allPlaylists.audioItems());
        assertEquals(Optional.empty(), allPlaylists.findParentPlaylist("Best hits"));
        assertEquals(Optional.empty(), allPlaylists.findParentPlaylist("50s hits"));
        assertEquals(Optional.empty(), allPlaylists.findParentPlaylist("60s hits"));

        assertTrue(allPlaylists.removePlaylist(newPlaylist));
        assertEquals(ImmutableSet.of(item5, item6, item9, item10), allPlaylists.audioItems());
        assertEquals(Optional.empty(), allPlaylists.findParentPlaylist("New 1"));

        assertFalse(allPlaylists.removeAudioItems(ImmutableSet.of(item4)));
        assertTrue(allPlaylists.removeAudioItems(ImmutableSet.of(item5)));
        assertEquals(ImmutableSet.of(item6, item9, item10), allPlaylists.audioItems());

        allPlaylists.clearPlaylistTrees();
        assertTrue(allPlaylists.subPlaylistTrees().isEmpty());
        assertFalse(allPlaylists.audioItems().isEmpty());

        allPlaylists.clearPlaylists();
        assertTrue(allPlaylists.audioPlaylists().isEmpty());
        assertTrue(allPlaylists.audioItems().isEmpty());
    }

    @Test
    @DisplayName("Addition and deletion of nested playlists")
    void additionAndDeletionOfPlaylistsTest() {
        AudioPlaylist<AudioItem> rock = new SimpleAudioPlaylist("Rock");
        AudioPlaylist<AudioItem> pop = new SimpleAudioPlaylist("Pop");
        PlaylistTree<AudioItem> fifties = new SimplePlaylistTree("50s", Collections.emptySet(), ImmutableSet.of(rock, pop));
        assertEquals(Optional.of(rock), fifties.findPlaylistByName("Rock"));
        assertEquals(Optional.of(pop), fifties.findPlaylistByName("Pop"));

        AudioPlaylist<AudioItem> sixtiesFavs = new SimpleAudioPlaylist("60s favorites");
        PlaylistTree<AudioItem> bestHits = new SimplePlaylistTree("Best hits", ImmutableSet.of(fifties), ImmutableSet.of(sixtiesFavs));
        assertEquals(Optional.of(sixtiesFavs), bestHits.findPlaylistByName("60s favorites"));

        PlaylistTree<AudioItem> selection = new SimplePlaylistTree("Selection");
        AudioPlaylist<AudioItem> weekFavs = new SimpleAudioPlaylist("This weeks' favorites");

        PlaylistTree<AudioItem> rootPlaylistTree = new SimplePlaylistTree("ROOT", ImmutableSet.of(bestHits, selection), ImmutableSet.of(weekFavs));

        assertEquals(Optional.of(rock), rootPlaylistTree.findPlaylistByName("Rock"));
        assertEquals(Optional.of(bestHits), rootPlaylistTree.findPlaylistTreeByName("Best hits"));
        assertEquals(Optional.of(weekFavs), rootPlaylistTree.findPlaylistByName("This weeks' favorites"));
        assertEquals(Optional.of(selection), rootPlaylistTree.findPlaylistTreeByName("Selection"));

        // ROOT
        // ├──Best hits
        // │  ├──50s
        // │  │  ├──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // ├──Selection
        // └──This weeks' favorites

        rootPlaylistTree.movePlaylist(rock, selection);

        assertFalse(fifties.audioPlaylists().contains(rock));
        assertEquals(Optional.of(selection), rootPlaylistTree.findParentPlaylist("Rock"));
        assertTrue(selection.audioPlaylists().contains(rock));

        rootPlaylistTree.movePlaylistTree(selection, fifties);

        assertFalse(rootPlaylistTree.subPlaylistTrees().contains(selection));
        assertEquals(Optional.of(fifties), rootPlaylistTree.findParentPlaylist("Selection"));
        assertTrue(fifties.subPlaylistTrees().contains(selection));
    }

    @Test
    @DisplayName("Compare and equals")
    void compareAndEqualsTest() {
        AudioItem item1 = mock(AudioItem.class, "item1");
        AudioItem item2 = mock(AudioItem.class, "item2");
        AudioPlaylist<AudioItem> sixtiesHits = new SimpleAudioPlaylist("60s hits", ImmutableList.of(item1, item2));

        PlaylistTree<AudioItem> bestHits =
                new SimplePlaylistTree("Best hits", Collections.emptySet(), ImmutableSet.of(sixtiesHits));
        assertEquals("Best hits", bestHits.name());
        bestHits.name("Best hits of my life");
        assertEquals("Best hits of my life", bestHits.name());

        AudioItem item3 = mock(AudioItem.class, "item3");
        AudioItem item4 = mock(AudioItem.class, "item4");
        AudioPlaylist<AudioItem> fiftiesHits = new SimpleAudioPlaylist("50s hits", ImmutableList.of(item3, item4));

        PlaylistTree<AudioItem> bestHits2 =
                new SimplePlaylistTree("Best hits", Collections.emptySet(), ImmutableSet.of(fiftiesHits));

        assertTrue(bestHits.compareTo(bestHits2) > 0);
        assertFalse(bestHits.equals(bestHits2));

        bestHits2.name("est hits of my life");
        assertTrue(bestHits.compareTo(bestHits2) < 0);

        bestHits2.removeAudioItems(ImmutableSet.of(item3, item4));
        fiftiesHits.name("60s hits");
        fiftiesHits.addAudioItems(ImmutableList.of(item1, item2));

        bestHits2.name("Best hits of my life");
        assertTrue(bestHits.equals(bestHits2));
        assertEquals(0, bestHits.compareTo(bestHits2));
        assertEquals("SimplePlaylistTree{audioPlaylists=[SimpleAudioPlaylist{name=60s hits}], subPlaylistTrees=[], name=Best hits of my life}", bestHits.toString());
    }
}
