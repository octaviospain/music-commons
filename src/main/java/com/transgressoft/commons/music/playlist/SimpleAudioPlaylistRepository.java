package com.transgressoft.commons.music.playlist;

import com.google.common.graph.*;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylistRepository implements AudioPlaylistRepository<AudioPlaylist> {

    private final AudioPlaylist ROOT_PLAYLIST;

    private MutableGraph<AudioPlaylist> playlistsTree = GraphBuilder.directed().build();

    public SimpleAudioPlaylistRepository() {
        ROOT_PLAYLIST = new SimpleAudioPlaylist("ROOT");
        playlistsTree.addNode(ROOT_PLAYLIST);
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
        addPlaylist(ROOT_PLAYLIST, playlist);
    }

    @Override
    public void addPlaylistsRecursively(AudioPlaylist parent, Collection<AudioPlaylist> playlists) {
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
        Optional<AudioPlaylist> parentOfMovedPlaylist = getParentPlaylist(movedPlaylist);
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
    public boolean isParentPlaylistRoot(AudioPlaylist playlist) {
        Optional<AudioPlaylist> parentPlaylist = getParentPlaylist(playlist);
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
