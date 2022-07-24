package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository<I extends AudioItem, P extends AudioPlaylist<I>, T extends PlaylistTree<I>> {

    T getRootPlaylistTree();
}
