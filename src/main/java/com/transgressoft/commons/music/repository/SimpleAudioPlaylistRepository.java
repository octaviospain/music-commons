package com.transgressoft.commons.music.repository;

import com.google.common.graph.*;
import com.transgressoft.commons.music.AudioItem;
import com.transgressoft.commons.music.playlist.*;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylistRepository implements AudioPlaylistRepository {

    private AudioPlaylist rootPlaylist;

    private MutableGraph<AudioPlaylist> playlistsTree = GraphBuilder.directed().build();

    public SimpleAudioPlaylistRepository() {
        rootPlaylist = new SimpleAudioPlaylist("ROOT");
        playlistsTree.addNode(rootPlaylist);
    }

    @Override
    public Graph<AudioPlaylist> getPlaylistsTree() {
        return playlistsTree;
    }

    @Override
    public void addPlaylist(AudioPlaylist parentPlaylist, AudioPlaylist playlist) {
        playlistsTree.putEdge(parentPlaylist, playlist);
        parentPlaylist.addChildPlaylist(playlist);
        if (! playlist.childPlaylists().isEmpty())
            addPlaylistsRecursively(playlist, playlist.childPlaylists());
    }

    @Override
    public void addPlaylistToRoot(AudioPlaylist playlist) {
        addPlaylist(rootPlaylist, playlist);
    }

    @Override
    public void addPlaylistsRecursively(AudioPlaylist parent, Collection<? extends AudioPlaylist> playlists) {
        playlists.forEach(childPlaylist -> {
            addPlaylist(parent, childPlaylist);
            if (! childPlaylist.childPlaylists().isEmpty())
                addPlaylistsRecursively(childPlaylist, childPlaylist.childPlaylists());
        });
    }

    @Override
    public void deletePlaylist(AudioPlaylist playlist) {
        getParentPlaylist(playlist).ifPresent(parent -> {
            playlistsTree.removeEdge(parent, playlist);
            parent.removeChildPlaylist(playlist);
        });
        playlistsTree.removeNode(playlist);
    }

    @Override
    public void movePlaylist(AudioPlaylist movedPlaylist, AudioPlaylist targetFolder) {
        var parentOfMovedPlaylist = getParentPlaylist(movedPlaylist);
        parentOfMovedPlaylist.ifPresent(parent -> {
            playlistsTree.removeEdge(parent, movedPlaylist);
            parent.removeChildPlaylist(movedPlaylist);
            playlistsTree.putEdge(targetFolder, movedPlaylist);
            targetFolder.addChildPlaylist(movedPlaylist);
        });
    }

    @Override
    public void removeAudioItems(Collection<? extends AudioItem> tracks) {
        playlistsTree.nodes().stream()
                .filter(playlist -> ! playlist.childPlaylists().isEmpty())
                .forEach(playlist -> playlist.removeAudioItems(tracks));
    }

    @Override
    public boolean containsPlaylist(String playlistName) {
        return playlistsTree.nodes().stream().anyMatch(playlist -> playlist.name().equals(playlistName));
    }

    @Override
    public Optional<AudioPlaylist> getParentPlaylist(AudioPlaylist playlist) {
        return playlistsTree.predecessors(playlist).stream().findFirst();
    }

    @Override
    public boolean isEmpty() {
        return playlistsTree.nodes().size() == 1  && playlistsTree.nodes().contains(rootPlaylist);
    }

    @Override
    public void clear() {
        rootPlaylist = new SimpleAudioPlaylist("ROOT");
        playlistsTree = GraphBuilder.directed().build();
    }
}
