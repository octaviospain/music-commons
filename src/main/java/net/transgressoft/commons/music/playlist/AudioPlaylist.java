package net.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableList;
import net.transgressoft.commons.music.AudioItem;

import java.util.List;
import java.util.Set;

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
