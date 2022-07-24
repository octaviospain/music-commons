package com.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
import com.google.common.base.*;
import com.google.common.collect.ImmutableSet;
import com.transgressoft.commons.music.AudioItem;

import java.util.Optional;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Base implementation of {@link PlaylistTree<I>} interface with generic methods for interface operations.
 *
 * @author Octavio Calleya
 */
public abstract class PlaylistTreeBase<I extends AudioItem> implements PlaylistTree<I> {

    private final Set<AudioPlaylist<I>> audioPlaylists;
    private final Set<PlaylistTree<I>> subPlaylistTrees;
    private String name;

    protected PlaylistTreeBase(String name, Set<PlaylistTree<I>> subPlaylistTrees, Set<AudioPlaylist<I>> audioPlaylists) {
        this.name = name;
        this.subPlaylistTrees = new HashSet<>(subPlaylistTrees);
        this.audioPlaylists = new HashSet<>(audioPlaylists);
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
    public boolean addPlaylist(AudioPlaylist<I> playlist) {
        return audioPlaylists.add(playlist);
    }

    @Override
    public boolean removePlaylist(AudioPlaylist<I> playlist) {
        return audioPlaylists.remove(playlist);
    }

    @Override
    public boolean addPlaylistTree(PlaylistTree<I> playlistTree) {
        return subPlaylistTrees.add(playlistTree);
    }

    @Override
    public ImmutableSet<AudioPlaylist<I>> audioPlaylists() {
        return ImmutableSet.copyOf(audioPlaylists);
    }

    @Override
    public ImmutableSet<PlaylistTree<I>> subPlaylistTrees() {
        return ImmutableSet.copyOf(subPlaylistTrees);
    }

    @Override
    public boolean removePlaylistTree(PlaylistTree<I> playlistTree) {
        return subPlaylistTrees.remove(playlistTree);
    }

    @Override
    public Optional<PlaylistTree<I>> findParentPlaylist(String playlistName) {
        Optional<AudioPlaylist<I>> foundPlaylist = audioPlaylists.stream()
                .filter(playlist -> playlist.name().equals(playlistName))
                .findAny();

        if (foundPlaylist.isPresent())
            return Optional.of(this);

        Optional<PlaylistTree<I>> foundPlaylistTree = subPlaylistTrees.stream()
                .filter(playlistTree -> playlistTree.name().equals(playlistName))
                .findAny();

        if (foundPlaylistTree.isPresent())
            return Optional.of(this);

        for (PlaylistTree<I> subPlaylistTree : subPlaylistTrees) {
            Optional<PlaylistTree<I>> result = subPlaylistTree.findParentPlaylist(playlistName);
            if (result.isPresent())
                return result;
        }

        return Optional.empty();
    }

    @SuppressWarnings ("unchecked")
    @Override
    public Optional<AudioPlaylist<I>> findPlaylistByName(String playlistName) {
        Optional<AudioPlaylist<I>> result = audioPlaylists.stream()
                .filter(playlist -> playlist.name().equals(playlistName))
                .findAny();

        if (! result.isPresent()) {
            for (PlaylistTree<I> subPlaylistTree : subPlaylistTrees) {
                result = subPlaylistTree.findPlaylistByName(playlistName);
                if (result.isPresent())
                    return result;
            }
            return Optional.empty();
        } else
            return result;
    }

    @SuppressWarnings ("unchecked")
    @Override
    public Optional<PlaylistTree<I>> findPlaylistTreeByName(String playlistTreeName) {
        Optional<PlaylistTree<I>> result = subPlaylistTrees.stream()
                .filter(playlist -> playlist.name().equals(playlistTreeName))
                .findAny();

        if (! result.isPresent()) {
            for (PlaylistTree<I> subPlaylistTree : subPlaylistTrees) {
                result = subPlaylistTree.findPlaylistTreeByName(playlistTreeName);
                if (result.isPresent())
                    return result;
            }
            return Optional.empty();
        } else
            return result;
    }

    @Override
    public void movePlaylist(AudioPlaylist<I> playlist, PlaylistTree<I> targetPlaylistTree) {
        findParentPlaylist(playlist.name()).ifPresent(parentPlaylist -> {
            parentPlaylist.removePlaylist(playlist);
            targetPlaylistTree.addPlaylist(playlist);
        });
    }

    @Override
    public void movePlaylistTree(PlaylistTree<I> subPlaylistTree, PlaylistTree<I> targetPlaylistTree) {
        findParentPlaylist(subPlaylistTree.name()).ifPresent(parentPlaylist -> {
            parentPlaylist.removePlaylistTree(subPlaylistTree);
            targetPlaylistTree.addPlaylistTree(subPlaylistTree);
        });
    }

    @Override
    public void clearPlaylistTrees() {
        subPlaylistTrees.clear();
    }

    @Override
    public void clearPlaylists() {
        audioPlaylists.clear();
    }

    @Override
    public ImmutableSet<AudioItem> audioItems() {
        Set<I> itemsFromPlaylists = audioPlaylists.stream()
                .flatMap(playlist -> playlist.audioItems().stream())
                .collect(Collectors.toSet());

        Set<AudioItem> itemsFromSubPlaylistTrees = subPlaylistTrees.stream()
                .flatMap(playlistTree -> playlistTree.audioItems().stream())
                .collect(Collectors.toSet());

        return ImmutableSet.<AudioItem>builder()
                .addAll(itemsFromPlaylists)
                .addAll(itemsFromSubPlaylistTrees)
                .build();
    }

    @Override
    public boolean removeAudioItems(Set<I> audioItems) {
        boolean removed = false;
        for (AudioPlaylist<I> audioPlaylist : audioPlaylists) {
            removed |= audioPlaylist.removeAudioItems(audioItems);
        }

        for (PlaylistTree<I> subPlaylistTree : subPlaylistTrees) {
            removed |= subPlaylistTree.removeAudioItems(audioItems);
        }
        return removed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistTreeBase<?> that = (PlaylistTreeBase<?>) o;
        return Objects.equal(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("audioPlaylists", audioPlaylists)
                .add("subPlaylistTrees", subPlaylistTrees)
                .add("name", name)
                .toString();
    }
}
