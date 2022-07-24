package com.transgressoft.commons.music.playlist;

import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public class SimplePlaylistTree extends PlaylistTreeBase<AudioItem> {

    public SimplePlaylistTree(String name, Set<PlaylistTree<AudioItem>> subPlaylistTrees, Set<AudioPlaylist<AudioItem>> audioPlaylists) {
        super(name, subPlaylistTrees, audioPlaylists);
    }

    public SimplePlaylistTree(String name) {
        super(name, Collections.emptySet(), Collections.emptySet());
    }
}
