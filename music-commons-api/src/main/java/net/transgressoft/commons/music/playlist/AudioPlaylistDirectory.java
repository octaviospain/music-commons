package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Set;

public interface AudioPlaylistDirectory<I extends AudioItem> extends AudioPlaylist<I> {

    <N extends AudioPlaylist<I>> Set<N> descendantPlaylists();

    <N extends AudioPlaylist<I>> boolean containsPlaylist(N playlist);
}
