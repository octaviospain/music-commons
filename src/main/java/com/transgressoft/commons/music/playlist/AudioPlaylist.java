package com.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableCollection;
import com.transgressoft.commons.music.AudioItem;

import java.util.Collection;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylist {

    String name();

    AudioPlaylist name(String name);

    ImmutableCollection<AudioItem> audioItems();

    boolean isEmpty();

    AudioPlaylist addAudioItems(Collection<AudioItem> audioItems);

    AudioPlaylist removeAudioItems(Collection<AudioItem> audioItems);

    ImmutableCollection<AudioPlaylist> childPlaylists();

    AudioPlaylist addChildPlaylist(AudioPlaylist audioPlaylist);

    AudioPlaylist removeChildPlaylist(AudioPlaylist audioPlaylist);
}
