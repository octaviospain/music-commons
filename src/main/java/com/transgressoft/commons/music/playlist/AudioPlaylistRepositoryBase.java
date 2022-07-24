package com.transgressoft.commons.music.playlist;

import com.google.common.graph.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public abstract class AudioPlaylistRepositoryBase<I extends AudioItem> implements AudioPlaylistRepository<I> {

    protected final AudioPlaylistFolder<I> ROOT_PLAYLIST;

    private MutableGraph<AudioPlaylist<I>> playlistsTree = GraphBuilder.directed().build();

    protected AudioPlaylistRepositoryBase(AudioPlaylistFolder<I> rootPlaylist) {
        ROOT_PLAYLIST = rootPlaylist;
        playlistsTree.addNode(ROOT_PLAYLIST);
    }

    @Override
    public Graph<AudioPlaylist<I>> getPlaylistsTree() {
        return playlistsTree;
    }

    @Override
    public void addPlaylist(AudioPlaylistFolder<I> parentPlaylist, AudioPlaylist<I> playlist) {
        playlistsTree.putEdge(parentPlaylist, playlist);
        parentPlaylist.includePlaylist(playlist);
        if (playlist instanceof AudioPlaylistFolder) {
            AudioPlaylistFolder<I> playlistFolder = (AudioPlaylistFolder<I>) playlist;
            addPlaylistsRecursively(playlistFolder, playlistFolder.includedPlaylists());
        }
    }

    @Override
    public void addFirstLevelPlaylist(AudioPlaylist<I> playlist) {
        addPlaylist(ROOT_PLAYLIST, playlist);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addPlaylistsRecursively(AudioPlaylistFolder<I> parent, Collection<AudioPlaylist<I>> playlists) {
        playlists.forEach(includedPlaylist -> {
            addPlaylist(parent, includedPlaylist);
            if (includedPlaylist instanceof AudioPlaylistFolder) {
                AudioPlaylistFolder<I> playlistFolder = (AudioPlaylistFolder<I>) includedPlaylist;
                addPlaylistsRecursively(playlistFolder, playlistFolder.includedPlaylists());
            }
        });
    }

    @Override
    public void deletePlaylist(AudioPlaylist<I> playlist) {
        getParentPlaylist(playlist).ifPresent(parent -> {
            playlistsTree.removeEdge(parent, playlist);
            parent.includePlaylist(playlist);
        });
        playlistsTree.removeNode(playlist);
    }

    @Override
    public void movePlaylist(AudioPlaylist<I> playlistToMove, AudioPlaylistFolder<I> destinationPlaylistFolder) {
        Optional<AudioPlaylistFolder<I>> parentOfMovedPlaylist = getParentPlaylist(playlistToMove);
        parentOfMovedPlaylist.ifPresent(parent -> {
            playlistsTree.removeEdge(parent, playlistToMove);
            parent.includePlaylist(playlistToMove);
            playlistsTree.putEdge(destinationPlaylistFolder, playlistToMove);
            destinationPlaylistFolder.includePlaylist(playlistToMove);
        });
    }

    @Override
    public void removeAudioItems(List<I> tracks) {
        playlistsTree.nodes().forEach(playlist -> playlist.removeAudioItems(tracks));
    }

    @Override
    public boolean containsPlaylist(String playlistName) {
        return playlistsTree.nodes().stream().anyMatch(playlist -> playlist.name().equals(playlistName));
    }

    @Override
    public Optional<AudioPlaylistFolder<I>> getParentPlaylist(AudioPlaylist<I> playlist) {
        Optional<AudioPlaylist<I>> foundParentPlaylist = playlistsTree.predecessors(playlist).stream().findFirst();

        Optional<AudioPlaylistFolder<I>> result = Optional.empty();

        if (foundParentPlaylist.isPresent()) {
            AudioPlaylist<I> found = foundParentPlaylist.get();
            if (! (found instanceof AudioPlaylistFolder)) {
                throw new NullPointerException("Parent of the playlist should be an instance of AudioPlaylistFolder");
            } else {
                return Optional.of((AudioPlaylistFolder<I>) found);
            }
        }
        return result;
    }

    @Override
    public boolean isParentPlaylistRoot(AudioPlaylist<I> playlist) {
        Optional<AudioPlaylistFolder<I>> parentPlaylist = getParentPlaylist(playlist);
        return parentPlaylist.map(audioPlaylist -> audioPlaylist.equals(ROOT_PLAYLIST)).orElse(false);
    }

    @Override
    public boolean isEmpty() {
        return playlistsTree.nodes().size() == 1  && playlistsTree.nodes().contains(ROOT_PLAYLIST);
    }

    @Override
    public void clear() {
        playlistsTree = GraphBuilder.directed().build();
        playlistsTree.addNode(ROOT_PLAYLIST);
    }
}
