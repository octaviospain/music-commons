package com.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public abstract class AudioPlaylistFolderBase<I extends AudioItem> extends AudioPlaylistBase<I> implements AudioPlaylistFolder<I> {

    private final Set<AudioPlaylist<I>> includedPlaylists;

    protected AudioPlaylistFolderBase(String name, Set<AudioPlaylist<I>> includedPlaylists) {
        super(name, Collections.emptyList());
        this.includedPlaylists = includedPlaylists;
    }

    protected AudioPlaylistFolderBase(String name) {
        this(name, new HashSet<>());
    }

    @Override
    public void addAudioItems(List<I> audioItems) {
        throw new UnsupportedOperationException("This implementation does not support adding items to the folder, only playlists");
    }

    @Override
    public void removeAudioItems(List<I> audioItems) {
        removeItemsFromIncludedPlaylists(audioItems);
    }

    @Override
    public ImmutableSet<AudioPlaylist<I>> includedPlaylists() {
        return ImmutableSet.copyOf(includedPlaylists);
    }

    @Override
    public void includePlaylist(AudioPlaylist<I> audioPlaylist) {
        includedPlaylists.add(audioPlaylist);
    }

    @Override
    public void removeIncludedPlaylist(AudioPlaylist<I> audioPlaylist) {
        includedPlaylists.remove(audioPlaylist);
    }

    @Override
    public void clearIncludedPlaylists() {
        includedPlaylists.clear();
    }

    @Override   // TODO remove when supporting adding items besides playlists
    public ImmutableList<I> audioItems() {
        return itemsIncludedFromAllPlaylists();
    }

    @Override
    public ImmutableList<I> itemsIncludedFromAllPlaylists() {
        return includedPlaylists.stream().flatMap(playlist -> {
            if (playlist instanceof AudioPlaylistFolder) {
                AudioPlaylistFolder<I> that = ((AudioPlaylistFolder<I>) playlist);
                return that.itemsIncludedFromAllPlaylists().stream();
            } else
                return playlist.audioItems().stream();
        }).collect(ImmutableList.toImmutableList());
    }

    @Override
    public void removeItemsFromIncludedPlaylists(List<I> audioItems) {
        includedPlaylists.forEach(playlist -> playlist.removeAudioItems(audioItems));
    }

    /**
     * Compares first by name, then by number of audio items, and finally by number of included playlists
     *
     * @param playlist  The {@link AudioPlaylist} to compare against this object
     * @return          The result of the comparison
     */
    @Override
    public int compareTo(AudioPlaylist<I> playlist) {
        int result;
        if (Objects.equal(name(), playlist.name())) {
            if (audioItems().size() - playlist.audioItems().size() == 0) {
                if (playlist instanceof AudioPlaylistFolder) {
                    result = includedPlaylists.size() - ((AudioPlaylistFolder<I>) playlist).includedPlaylists().size();
                } else {
                    result = includedPlaylists.size();
                }
            } else {
                result = audioItems().size() - playlist.audioItems().size();
            }
        } else {
            result = name().compareTo(playlist.name());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioPlaylistFolder<I> that = (AudioPlaylistFolder<I>) o;
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
                .add("audioItemsFromAllPlaylists", itemsIncludedFromAllPlaylists().size())
                .toString();
    }
}
