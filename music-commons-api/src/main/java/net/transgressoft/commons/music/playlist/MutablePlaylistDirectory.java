package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.ListIterator;

public interface MutablePlaylistDirectory<I extends AudioItem> extends MutablePlaylistNode<I> {

    <P extends MutablePlaylistNode<I>> void addPlaylist(P playlist);

    <P extends MutablePlaylistNode<I>> void removePlaylist(P playlist);

    <P extends MutablePlaylistNode<I>> boolean containsPlaylist(P playlist);

    void clearAudioItemsFromPlaylists();

    void clearDescendantPlaylists();

    boolean isEmptyOfPlaylists();

    <P extends MutablePlaylistNode<I>> ListIterator<P> descendantPlaylistsIterator();
}
