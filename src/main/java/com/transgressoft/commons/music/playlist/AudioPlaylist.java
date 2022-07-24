package com.transgressoft.commons.music.playlist;

import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.List;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylist<I extends AudioItem> {

    String name();

    void name(String name);

    ImmutableList<I> audioItems();

    boolean isEmpty();

    void addAudioItems(List<I> audioItems);

    void removeAudioItems(List<I> audioItems);

    ImmutableSet<AudioPlaylist<I>> childPlaylists();

    void addChildPlaylist(AudioPlaylist<I> audioPlaylist);

    void removeChildPlaylist(AudioPlaylist<I>audioPlaylist);
}
