package com.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
import com.google.common.base.*;
import com.google.common.collect.ImmutableList;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public abstract class AudioPlaylistBase<I extends AudioItem> implements AudioPlaylist<I> {

    private String name;
    private List<I> audioItems;

    protected AudioPlaylistBase(String name, List<I> audioItems) {
        this.name = name;
        this.audioItems = audioItems;
    }

    protected AudioPlaylistBase(String name) {
        this(name, Collections.emptyList());
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
    public void clear() {
        audioItems.clear();
    }

    /**
     * Compares first by name and then by number of audio items.
     *
     * @param playlist  The {@link AudioPlaylist} to compare against this object
     * @return          The result of the comparison
     */
    @Override
    public int compareTo(AudioPlaylist<I> playlist) {
        if (Objects.equal(name, playlist.name()))
            return audioItems.size() - playlist.audioItems().size();
        else
            return name.compareTo(playlist.name());
    }

    @SuppressWarnings ("unchecked")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioPlaylist<I> that = (AudioPlaylist<I>) o;
        return Objects.equal(name, that.name()) &&
                Objects.equal(audioItems, that.audioItems());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, audioItems);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .toString();
    }
}

