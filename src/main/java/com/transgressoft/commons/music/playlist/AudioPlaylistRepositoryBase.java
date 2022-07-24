package com.transgressoft.commons.music.playlist;

import com.google.common.graph.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public abstract class AudioPlaylistRepositoryBase<I extends AudioItem, P extends AudioPlaylist<I>, F extends AudioPlaylistFolder<I>>
        implements AudioPlaylistRepository<I, P, F> {

    private final F rootPlaylist;

    private MutableGraph<AudioPlaylist<I>> playlistsTree = GraphBuilder.directed().build();

    @SuppressWarnings("unchecked")
    protected AudioPlaylistRepositoryBase() {
        rootPlaylist = (F) new SimpleAudioPlaylistFolder("ROOT");
        playlistsTree.addNode(rootPlaylist);
    }

    @Override
    public Graph<AudioPlaylist<I>> getPlaylistsTree() {
        return playlistsTree;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addPlaylist(F parentPlaylist, P playlist) {
        playlistsTree.putEdge(parentPlaylist, playlist);
        parentPlaylist.includePlaylist(playlist);
        if (playlist instanceof AudioPlaylistFolder) {
            F playlistFolder = (F) playlist;
            addPlaylistsRecursively(playlistFolder, (Collection<P>) playlistFolder.includedPlaylists());
        }
    }

    @Override
    public void addFirstLevelPlaylist(P playlist) {
        addPlaylist(rootPlaylist, playlist);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addPlaylistsRecursively(F parent, Collection<P> playlists) {
        playlists.forEach(includedPlaylist -> {
            addPlaylist(parent, includedPlaylist);
            if (includedPlaylist instanceof AudioPlaylistFolder) {
                F playlistFolder = (F) includedPlaylist;
                addPlaylistsRecursively(playlistFolder, (Collection<P>) playlistFolder.includedPlaylists());
            }
        });
    }

    @Override
    public void deletePlaylist(P playlist) {
        getParentPlaylist(playlist).ifPresent(parent -> {
            playlistsTree.removeEdge(parent, playlist);
            parent.removeIncludedPlaylist(playlist);
        });
        playlistsTree.removeNode(playlist);
    }

    @Override
    public void movePlaylist(P playlistToMove, F destinationPlaylistFolder) {
        Optional<F> parentOfMovedPlaylist = getParentPlaylist(playlistToMove);
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

    @SuppressWarnings("unchecked")
    @Override
    public Optional<F> getParentPlaylist(P playlist) {
        Optional<AudioPlaylist<I>> foundParentPlaylist = playlistsTree.predecessors(playlist).stream().findFirst();

        Optional<F> result = Optional.empty();

        if (foundParentPlaylist.isPresent()) {
            AudioPlaylist<I> found = foundParentPlaylist.get();
            if (! (found instanceof AudioPlaylistFolder)) {
                throw new NullPointerException("Parent of the playlist should be an instance of AudioPlaylistFolder");
            } else {
                return Optional.of((F) found);
            }
        }
        return result;
    }

    @Override
    public boolean isParentPlaylistRoot(P playlist) {
        Optional<F> parentPlaylist = getParentPlaylist(playlist);
        return parentPlaylist.map(audioPlaylist -> audioPlaylist.equals(rootPlaylist)).orElse(false);
    }

    @Override
    public boolean isEmpty() {
        return playlistsTree.nodes().size() == 1  && playlistsTree.nodes().contains(rootPlaylist);
    }

    @Override
    public void clear() {
        playlistsTree = GraphBuilder.directed().build();
        playlistsTree.addNode(rootPlaylist);
    }
}
