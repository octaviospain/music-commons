package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.List;

public interface AudioPlaylistBuilder<P extends MutablePlaylistNode<I>, I extends AudioItem> {

    P build();

    AudioPlaylistBuilder<P, I> ancestor(MutablePlaylistDirectory<I> ancestor);

    AudioPlaylistBuilder<P, I> audioItems(List<I> audioItems);
}
