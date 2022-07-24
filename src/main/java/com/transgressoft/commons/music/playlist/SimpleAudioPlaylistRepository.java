package com.transgressoft.commons.music.playlist;

import com.transgressoft.commons.music.AudioItem;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylistRepository extends AudioPlaylistRepositoryBase<AudioPlaylist<AudioItem>> {

    public SimpleAudioPlaylistRepository() {
        super(new SimpleAudioPlaylist("ROOT"));
    }
}
