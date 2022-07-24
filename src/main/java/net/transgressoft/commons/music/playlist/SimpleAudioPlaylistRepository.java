package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

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
