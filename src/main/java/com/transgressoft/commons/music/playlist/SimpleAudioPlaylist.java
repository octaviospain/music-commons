package com.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylist implements AudioPlaylist<AudioItem> {

    private String name;
    private List<AudioItem> audioItems;
    private Set<AudioPlaylist<AudioItem>> includedPlaylists;

    public SimpleAudioPlaylist(String name, List<AudioItem> audioItems, Set<AudioPlaylist<AudioItem>> includedPlaylists) {
        this.name = name;
        this.audioItems = audioItems;
        this.includedPlaylists = includedPlaylists;
    }

    public SimpleAudioPlaylist(String name, List<AudioItem> audioItems) {
        this(name, audioItems, Collections.emptySet());
    }

    public SimpleAudioPlaylist(String name) {
        this(name, Collections.emptyList(), Collections.emptySet());
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
    public ImmutableList<AudioItem> audioItems() {
        return ImmutableList.copyOf(audioItems);
    }

    @Override
    public boolean isEmpty() {
        return audioItems.isEmpty();
    }

    @Override
    public void addAudioItems(List<AudioItem> audioItems) {
        this.audioItems.addAll(audioItems);
    }

    @Override
    public void removeAudioItems(List<AudioItem> audioItems) {
        this.audioItems.removeAll(audioItems);
    }

    @Override
    public ImmutableSet<AudioPlaylist<AudioItem>> includedPlaylists() {
        return ImmutableSet.copyOf(includedPlaylists);
    }

    @Override
    public void includePlaylist(AudioPlaylist<AudioItem> audioPlaylist) {
        includedPlaylists.add(audioPlaylist);
    }

    @Override
    public void removeIncludedPlaylist(AudioPlaylist<AudioItem> audioPlaylist) {
        includedPlaylists.remove(audioPlaylist);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleAudioPlaylist that = (SimpleAudioPlaylist) o;
        return com.google.common.base.Objects.equal(name, that.name) &&
                com.google.common.base.Objects.equal(audioItems, that.audioItems) &&
                com.google.common.base.Objects.equal(includedPlaylists, that.includedPlaylists);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, audioItems, includedPlaylists);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .toString();
    }
}

