package com.transgressoft.commons.music.playlist;

import com.transgressoft.commons.music.AudioItem;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository<I extends AudioItem, P extends AudioPlaylist<I>, T extends PlaylistTree<I>> {

    T getRootPlaylistTree();
}
