package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.List;

/**
 * Represents a mutable data object that stores a collection of {@link AudioItem}s.
 *
 * @author Octavio Calleya
 */
public interface AudioPlaylist<I extends AudioItem> extends PlaylistItem<I> {

    boolean isEmpty();

    AudioPlaylist<I> addAudioItems(List<I> audioItems);

    AudioPlaylist<I> clear();
}
