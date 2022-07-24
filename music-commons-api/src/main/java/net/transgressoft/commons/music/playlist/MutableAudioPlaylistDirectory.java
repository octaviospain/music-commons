package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Set;

public interface MutableAudioPlaylistDirectory<I extends AudioItem> extends AudioPlaylistDirectory<I>, MutableAudioPlaylist<I> {

    <N extends AudioPlaylist<I>> void addPlaylists(N... playlists);

    <N extends AudioPlaylist<I>> void addPlaylists(Set<N> playlists);

    <N extends AudioPlaylist<I>> void removePlaylists(N... playlists);

    <N extends AudioPlaylist<I>> void removePlaylists(Set<N> playlists);
}
