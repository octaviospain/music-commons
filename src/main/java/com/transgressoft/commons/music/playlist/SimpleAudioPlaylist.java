package com.transgressoft.commons.music.playlist;

import com.google.common.base.*;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.transgressoft.commons.music.*;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylist implements AudioPlaylist {

    private final String name;
    private final Collection<AudioItem> audioItems;
    private final Set<AudioPlaylist> childPlaylists;

    public SimpleAudioPlaylist(String name, Collection<AudioItem> audioItems, Set<AudioPlaylist> childPlaylists) {
        this.name = name;
        this.audioItems = audioItems;
        this.childPlaylists = childPlaylists;
    }

    public SimpleAudioPlaylist(String name, Collection<AudioItem> audioItems) {
        this.name = name;
        this.audioItems = audioItems;
        this.childPlaylists = ImmutableSet.of();
    }

    public SimpleAudioPlaylist(String name) {
        this.name = name;
        this.audioItems = ImmutableList.of();
        this.childPlaylists = ImmutableSet.of();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AudioPlaylist name(String name) {
        return new SimpleAudioPlaylist(name, audioItems, childPlaylists);
    }

    @Override
    public ImmutableCollection<AudioItem> audioItems() {
        return ImmutableList.copyOf(audioItems);
    }

    @Override
    public boolean isEmpty() {
        return audioItems.isEmpty();
    }

    @Override
    public AudioPlaylist addAudioItems(Collection<AudioItem> audioItems) {
        Collection<AudioItem> list = Lists.newArrayList(this.audioItems);
        list.addAll(audioItems);
        return new SimpleAudioPlaylist(name, list);
    }

    @Override
    public AudioPlaylist removeAudioItems(Collection<AudioItem> audioItems) {
        Collection<AudioItem> list = Lists.newArrayList(this.audioItems);
        list.removeAll(audioItems);
        return new SimpleAudioPlaylist(name, list);
    }

    @Override
    public ImmutableSet<AudioPlaylist> childPlaylists() {
        return ImmutableSet.copyOf(childPlaylists);
    }

    @Override
    public AudioPlaylist addChildPlaylist(AudioPlaylist audioPlaylist) {
        Set<AudioPlaylist> set = Sets.newHashSet(childPlaylists);
        set.add(audioPlaylist);
        return new SimpleAudioPlaylist(name, audioItems, set);
    }

    @Override
    public AudioPlaylist removeChildPlaylist(AudioPlaylist audioPlaylist) {
        childPlaylists.remove(audioPlaylist);
        Set<AudioPlaylist> set = Sets.newHashSet(childPlaylists);
        set.add(audioPlaylist);
        return new SimpleAudioPlaylist(name, audioItems, set);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleAudioPlaylist that = (SimpleAudioPlaylist) o;
        return Objects.equal(name, that.name) &&
                Objects.equal(audioItems, that.audioItems) &&
                Objects.equal(childPlaylists, that.childPlaylists);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, audioItems, childPlaylists);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .toString();
    }
}
