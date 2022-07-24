package com.transgressoft.commons.music.playlist;

import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylist extends AudioPlaylistBase<AudioItem> {

    public SimpleAudioPlaylist(String name, Collection<AudioItem> audioItems, Set<AudioPlaylist<AudioItem>> childPlaylists) {
        super(name, audioItems, childPlaylists);
    }

    public SimpleAudioPlaylist(String name, Collection<AudioItem> audioItems) {
        super(name, audioItems);
    }

    public SimpleAudioPlaylist(String name) {
        super(name);
    }
}
