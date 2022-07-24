package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.ListIterator;

public interface MutablePlaylistDirectory<I extends AudioItem, D extends MutablePlaylistDirectory<I, D>> extends MutablePlaylistNode<I> {

    void addPlaylist(D playlist);

    <P extends MutablePlaylistNode<?>> void removePlaylist(P playlist);

    boolean containsPlaylist(D playlist);

    void clearAudioItemsFromPlaylists();

    void clearDescendantPlaylists();

    boolean isEmptyOfPlaylists();

    ListIterator<D> descendantPlaylistsIterator();
}
