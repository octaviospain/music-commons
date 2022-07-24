package com.transgressoft.commons.music.playlist;

import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylistFolder extends AudioPlaylistFolderBase<AudioItem> implements AudioPlaylistFolder<AudioItem> {

    public SimpleAudioPlaylistFolder(String name, Set<AudioPlaylist<AudioItem>> includedPlaylists) {
        super(name, includedPlaylists);
    }

    public SimpleAudioPlaylistFolder(String name) {
        this(name, new HashSet<>());
    }
}
