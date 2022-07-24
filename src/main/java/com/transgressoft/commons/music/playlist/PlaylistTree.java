package com.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableSet;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public interface PlaylistTree<I extends AudioItem> {

    String name();

    void name(String name);

    boolean addPlaylist(AudioPlaylist<I> playlist);

    boolean removePlaylist(AudioPlaylist<I> playlist);

    boolean addPlaylistTree(PlaylistTree<I> playlistTree);

    ImmutableSet<AudioPlaylist<I>> audioPlaylists();

    ImmutableSet<PlaylistTree<I>> subPlaylistTrees();

    boolean removePlaylistTree(PlaylistTree<I> playlistTree);

    Optional<PlaylistTree<I>> findParentPlaylist(String playlistName);

    Optional<AudioPlaylist<I>> findPlaylistByName(String playlistName);

    Optional<PlaylistTree<I>> findPlaylistTreeByName(String playlistName);

    void movePlaylist(AudioPlaylist<I> playlist, PlaylistTree<I> targetPlaylistTree);

    void movePlaylistTree(PlaylistTree<I> subPlaylistTree, PlaylistTree<I> targetPlaylistTree);

    void clearPlaylistTrees();

    void clearPlaylists();

    ImmutableSet<AudioItem> audioItems();

    boolean removeAudioItems(Set<I> audioItems);
}
