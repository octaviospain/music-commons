package com.transgressoft.commons.music.playlist;

import com.transgressoft.commons.music.AudioItem;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylistRepository extends AudioPlaylistRepositoryBase<AudioItem, AudioPlaylist<AudioItem>, PlaylistTree<AudioItem>> {

    public SimpleAudioPlaylistRepository() {
        super();
    }

    public SimpleAudioPlaylistRepository(PlaylistTree<AudioItem> playlistTree) {
        super(playlistTree);
    }
}
