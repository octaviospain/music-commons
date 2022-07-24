package com.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylistFolder extends SimpleAudioPlaylist implements AudioPlaylistFolder<AudioItem> {

    private final Set<AudioPlaylist<AudioItem>> includedPlaylists;

    public SimpleAudioPlaylistFolder(String name, Set<AudioPlaylist<AudioItem>> includedPlaylists) {
        super(name, Collections.emptyList());
        this.includedPlaylists = includedPlaylists;
    }

    public SimpleAudioPlaylistFolder(String name) {
        this(name, new HashSet<>());
    }

    @Override
    public void addAudioItems(List<AudioItem> audioItems) {
        throw new UnsupportedOperationException("This implementation does not support adding items to the folder, only playlists");
    }

    @Override
    public void removeAudioItems(List<AudioItem> audioItems) {
        removeItemsFromIncludedPlaylists(audioItems);
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

    @Override   // TODO remove when supporting adding items besides playlists
    public ImmutableList<AudioItem> audioItems() {
        return itemsIncludedFromAllPlaylists();
    }

    @Override
    public ImmutableList<AudioItem> itemsIncludedFromAllPlaylists() {
        return includedPlaylists.stream().flatMap(playlist -> {
            if (playlist instanceof AudioPlaylistFolder) {
                AudioPlaylistFolder<AudioItem> that = ((AudioPlaylistFolder<AudioItem>) playlist);
                return that.itemsIncludedFromAllPlaylists().stream();
            } else
                return playlist.audioItems().stream();
        }).collect(ImmutableList.toImmutableList());
    }

    @Override
    public void removeItemsFromIncludedPlaylists(List<AudioItem> audioItems) {
        includedPlaylists.forEach(playlist -> playlist.removeAudioItems(audioItems));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleAudioPlaylistFolder that = (SimpleAudioPlaylistFolder) o;
        return com.google.common.base.Objects.equal(name(), that.name()) &&
                com.google.common.base.Objects.equal(audioItems(), that.audioItems()) &&
                com.google.common.base.Objects.equal(includedPlaylists, that.includedPlaylists());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name(), audioItems(), includedPlaylists);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name())
                .add("includedPlaylists", includedPlaylists.size())
                .toString();
    }
}
