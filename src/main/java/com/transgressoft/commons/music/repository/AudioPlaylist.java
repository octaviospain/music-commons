package com.transgressoft.commons.music.repository;

import com.google.common.collect.ImmutableCollection;
import com.transgressoft.commons.music.AudioItem;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylist {

    String name();

    AudioPlaylist name(String name);

    ImmutableCollection<AudioItem> audioItems();

    AudioPlaylist addAudioItem(AudioItem audioItem);

    ImmutableCollection<AudioPlaylist> childPlaylists();

    AudioPlaylist addChildPlaylist(AudioPlaylist audioPlaylist);
}
