package com.transgressoft.commons.music.playlist;

import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.List;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistFolder<I extends AudioItem> extends AudioPlaylist<I> {

    ImmutableSet<AudioPlaylist<I>> includedPlaylists();

    void includePlaylist(AudioPlaylist<I> audioPlaylist);

    void removeIncludedPlaylist(AudioPlaylist<I> audioPlaylist);

    void clearIncludedPlaylists();

    ImmutableList<I> itemsIncludedFromAllPlaylists();

    void removeItemsFromIncludedPlaylists(List<I> audioItems);
}
