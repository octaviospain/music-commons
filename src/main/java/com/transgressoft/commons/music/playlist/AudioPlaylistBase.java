package com.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public class AudioPlaylistBase<I extends AudioItem> implements AudioPlaylist<I> {

    protected String name;
    protected Collection<I> audioItems;
    protected  Set<AudioPlaylist<I>> childPlaylists;

    public AudioPlaylistBase(String name, Collection<I> audioItems, Set<AudioPlaylist<I>> childPlaylists) {
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
    public void name(String name) {
        this.name = name;
    }

    @Override
    public ImmutableList<I> audioItems() {
        return ImmutableList.copyOf(audioItems);
    }

    @Override
    public boolean isEmpty() {
        return audioItems.isEmpty();
    }

    @Override
    public void addAudioItems(List<I> audioItems) {
        this.audioItems.addAll(audioItems);
    }

    @Override
    public void removeAudioItems(List<I> audioItems) {
        this.audioItems.removeAll(audioItems);
    }

    @Override
    public ImmutableSet<AudioPlaylist<I>> childPlaylists() {
        return ImmutableSet.copyOf(childPlaylists);
    }

    @Override
    public void addChildPlaylist(AudioPlaylist<I> audioPlaylist) {
        childPlaylists.add(audioPlaylist);
    }

    @Override
    public void removeChildPlaylist(AudioPlaylist<I> audioPlaylist) {
        this.childPlaylists.remove(audioPlaylist);
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
