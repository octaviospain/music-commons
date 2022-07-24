package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Set;

public interface MutableAudioPlaylistDirectory<I extends AudioItem> extends AudioPlaylistDirectory<I>, MutableAudioPlaylist<I> {

    <N extends AudioPlaylist<I>> void addPlaylist(N... playlist);

    <N extends AudioPlaylist<I>> void addAllPlaylists(Set<N> playlists);

    <N extends AudioPlaylist<I>> void removePlaylist(N playlist);
}
