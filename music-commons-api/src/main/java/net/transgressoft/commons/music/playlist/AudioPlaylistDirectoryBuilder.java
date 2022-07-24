package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Set;

interface AudioPlaylistDirectoryBuilder<D extends MutablePlaylistDirectory<I>, I extends AudioItem> extends AudioPlaylistBuilder<D, I> {

    AudioPlaylistDirectoryBuilder<D, I> descendantPlaylists(Set<MutablePlaylistNode<AudioItem>> descendantPlaylists);
}
