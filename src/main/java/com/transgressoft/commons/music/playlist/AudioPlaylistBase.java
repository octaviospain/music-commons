package com.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public abstract class AudioPlaylistBase<I extends AudioItem> implements AudioPlaylist {

    protected final String name;
    protected final Collection<I> audioItems;
    protected final Set<AudioPlaylist> childPlaylists;

    public AudioPlaylistBase(String name, Collection<I> audioItems, Set<AudioPlaylist> childPlaylists) {
        this.name = name;
        this.audioItems = audioItems;
        this.childPlaylists = childPlaylists;
    }

    public AudioPlaylistBase(String name, Collection<I> audioItems) {
        this.name = name;
        this.audioItems = audioItems;
        this.childPlaylists = ImmutableSet.of();
    }

    public AudioPlaylistBase(String name) {
        this.name = name;
        this.audioItems = ImmutableList.of();
        this.childPlaylists = ImmutableSet.of();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ImmutableCollection<AudioItem> audioItems() {
        return ImmutableList.copyOf(audioItems);
    }

    @Override
    public ImmutableSet<AudioPlaylist> childPlaylists() {
        return ImmutableSet.copyOf(childPlaylists);
    }

    @Override
    public boolean isEmpty() {
        return audioItems.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioPlaylistBase<I> that = (AudioPlaylistBase<I>) o;
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
