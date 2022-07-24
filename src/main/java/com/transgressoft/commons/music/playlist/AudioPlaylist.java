package com.transgressoft.commons.music.playlist;

import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.Collection;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylist<I extends AudioItem> {

    String name();

    AudioPlaylist<I> name(String name);

    ImmutableCollection<I> audioItems();

    boolean isEmpty();

    AudioPlaylist<I> addAudioItems(Collection<I> audioItems);

    AudioPlaylist<I> removeAudioItems(Collection<I> audioItems);

    ImmutableSet<AudioPlaylist<I>> childPlaylists();

    AudioPlaylist<I> addChildPlaylist(AudioPlaylist<I> audioPlaylist);

    AudioPlaylist<I> removeChildPlaylist(AudioPlaylist<I> audioPlaylist);
}
