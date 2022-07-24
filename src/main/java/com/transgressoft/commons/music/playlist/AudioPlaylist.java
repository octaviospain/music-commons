package com.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableList;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylist<I extends AudioItem> {

    String name();

    void name(String name);

    ImmutableList<I> audioItems();

    boolean isEmpty();

    void addAudioItems(List<I> audioItems);

    boolean removeAudioItems(Set<I> audioItems);

    void clear();
}
