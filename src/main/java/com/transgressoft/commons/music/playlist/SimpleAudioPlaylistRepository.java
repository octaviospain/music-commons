package com.transgressoft.commons.music.playlist;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylistRepository extends AudioPlaylistRepositoryBase<AudioPlaylist> {

    public SimpleAudioPlaylistRepository() {
        super(new SimpleAudioPlaylist("ROOT"));
    }
}
