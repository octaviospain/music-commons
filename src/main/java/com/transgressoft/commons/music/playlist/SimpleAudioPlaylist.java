package com.transgressoft.commons.music.playlist;

import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylist extends AudioPlaylistBase<AudioItem> {

    public SimpleAudioPlaylist(String name, Collection<AudioItem> audioItems, Set<AudioPlaylist> childPlaylists) {
        super(name, audioItems, childPlaylists);
    }

    public SimpleAudioPlaylist(String name, Collection<AudioItem> audioItems) {
        super(name, audioItems);
    }

    public SimpleAudioPlaylist(String name) {
        super(name);
    }

    @Override
    public AudioPlaylist name(String name) {
        return new SimpleAudioPlaylist(name, audioItems, childPlaylists);
    }

    @Override
    public ImmutableSet<AudioPlaylist> childPlaylists() {
        return ImmutableSet.copyOf(childPlaylists);
    }

    @Override
    public SimpleAudioPlaylist addAudioItems(Collection<AudioItem> audioItems) {
        Collection<AudioItem> list = Lists.newArrayList(this.audioItems);
        list.addAll(audioItems);
        return new SimpleAudioPlaylist(name, list);
    }

    @Override
    public SimpleAudioPlaylist removeAudioItems(Collection<? extends AudioItem> audioItems) {
        Collection<AudioItem> list = Lists.newArrayList(this.audioItems);
        list.removeAll(audioItems);
        return new SimpleAudioPlaylist(name, list);
    }

    @Override
    public SimpleAudioPlaylist addChildPlaylist(AudioPlaylist audioPlaylist) {
        Set<AudioPlaylist> set = Sets.newHashSet(childPlaylists());
        set.add(audioPlaylist);
        return new SimpleAudioPlaylist(name, audioItems, set);
    }

    @Override
    public SimpleAudioPlaylist removeChildPlaylist(AudioPlaylist audioPlaylist) {
        childPlaylists.remove(audioPlaylist);
        Set<AudioPlaylist> set = Sets.newHashSet(childPlaylists);
        set.add(audioPlaylist);
        return new SimpleAudioPlaylist(name, audioItems, set);
    }
}
