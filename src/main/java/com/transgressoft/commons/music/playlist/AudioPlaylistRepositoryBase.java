package com.transgressoft.commons.music.playlist;

import com.google.common.graph.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public abstract class AudioPlaylistRepositoryBase<P extends AudioPlaylist<I>, I extends AudioItem> extends AbstractGraph<P> implements AudioPlaylistRepository<P, I> {

    protected final P ROOT_PLAYLIST;

    private MutableGraph<P> playlistsTree = GraphBuilder.directed().build();

    protected AudioPlaylistRepositoryBase(P rootPlaylist) {
        ROOT_PLAYLIST = rootPlaylist;
        playlistsTree.addNode(ROOT_PLAYLIST);
    }

    @Override
    public Graph<P> getPlaylistsTree() {
        return playlistsTree;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addPlaylist(P parentPlaylist, P playlist) {
        playlistsTree.putEdge(parentPlaylist, playlist);
        parentPlaylist.includePlaylist(playlist);
        if (! playlist.includedPlaylists().isEmpty())
            addPlaylistsRecursively(playlist, (Collection<P>) playlist.includedPlaylists());
    }

    @Override
    public void addPlaylistToRoot(P playlist) {
        addPlaylist(ROOT_PLAYLIST, playlist);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addPlaylistsRecursively(P parent, Collection<P> playlists) {
        playlists.forEach(childPlaylist -> {
            addPlaylist(parent, childPlaylist);
            if (! childPlaylist.includedPlaylists().isEmpty())
                addPlaylistsRecursively(childPlaylist, (Collection<P>) childPlaylist.includedPlaylists());
        });
    }

    @Override
    public void deletePlaylist(P playlist) {
        getParentPlaylist(playlist).ifPresent(parent -> {
            playlistsTree.removeEdge(parent, playlist);
            parent.includePlaylist(playlist);
        });
        playlistsTree.removeNode(playlist);
    }

    @Override
    public void movePlaylist(P movedPlaylist, P targetFolder) {
        Optional<P> parentOfMovedPlaylist = getParentPlaylist(movedPlaylist);
        parentOfMovedPlaylist.ifPresent(parent -> {
            playlistsTree.removeEdge(parent, movedPlaylist);
            parent.includePlaylist(movedPlaylist);
            playlistsTree.putEdge(targetFolder, movedPlaylist);
            targetFolder.includePlaylist(movedPlaylist);
        });
    }

    @Override
    public void removeAudioItems(List<I> tracks) {
        playlistsTree.nodes().stream()
                .filter(playlist -> ! playlist.includedPlaylists().isEmpty())
                .forEach(playlist -> playlist.removeAudioItems(tracks));
    }

    @Override
    public boolean containsPlaylist(String playlistName) {
        return playlistsTree.nodes().stream().anyMatch(playlist -> playlist.name().equals(playlistName));
    }

    @Override
    public Optional<P> getParentPlaylist(P playlist) {
        return playlistsTree.predecessors(playlist).stream().findFirst();
    }

    @Override
    public boolean isParentPlaylistRoot(P playlist) {
        Optional<P> parentPlaylist = getParentPlaylist(playlist);
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

    @Override
    public Set<P> nodes() {
        return null;
    }

    @Override
    public boolean isDirected() {
        return true;
    }

    @Override
    public boolean allowsSelfLoops() {
        return false;
    }

    @Override
    public ElementOrder<P> nodeOrder() {
        return null;
    }

    @Override
    public Set<P> adjacentNodes(P node) {
        return null;
    }

    @Override
    public Set<P> predecessors(P node) {
        return null;
    }

    @Override
    public Set<P> successors(P node) {
        return null;
    }
}
