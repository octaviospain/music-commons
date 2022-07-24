package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.ListIterator;

public interface MutablePlaylistDirectory<I extends AudioItem> extends MutablePlaylistNode<I> {

    void addPlaylist(MutablePlaylistNode<I> playlist);

    void removePlaylist(MutablePlaylistNode<?> playlist);

    boolean containsPlaylist(MutablePlaylistNode<I> playlist);

    void clearAudioItemsFromPlaylists();

    void clearDescendantPlaylists();

    boolean isEmptyOfPlaylists();

    ListIterator<MutablePlaylistNode<I>> descendantPlaylistsIterator();
}
