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

    /**
     * Includes an {@link AudioPlaylist} in the repository, at the top of the hierarchy,
     * this is, at the first level of the tree, where is children of the root playlist.
     *
     * @param playlist  The {@link AudioPlaylist} to include
     */
    @Override
    public void addFirstLevelPlaylist(P playlist) {
        addPlaylist(rootPlaylist, playlist);
    }

    /**
     * Includes an {@link AudioPlaylist} as a child of a given {@link AudioPlaylistFolder} into the
     * repository. If the given {@link AudioPlaylistFolder} does not exist in the repository yet,
     * the {@link AudioPlaylistFolder} is added to the top of the hierarchy.
     * If the given {@link AudioPlaylist} is an instance of {@link AudioPlaylistFolder} too,
     * all its contained playlists are added recursively.
     *
     * @param playlistFolder    The {@link AudioPlaylistFolder} of the playlist
     * @param playlist          The {@link AudioPlaylist} to add to the folder playlist
     */
    @SuppressWarnings("unchecked")
    @Override
    public void addPlaylist(F playlistFolder, P playlist) {
        if (! playlistsTree.nodes().contains(playlistFolder))
            playlistsTree.putEdge(rootPlaylist, playlistFolder);

        playlistsTree.putEdge(playlistFolder, playlist);

        //  Includes the playlist into its parent playlist folder to ensure consistency
        //  If it was already included, nothing changes because the underlying data structure is a Set,
        //  assuming that implementation of equals() and hashCode() is consistent.
        playlistFolder.includePlaylist(playlist);

        if (playlist instanceof AudioPlaylistFolder) {
            F audioPlaylistFolder = (F) playlist;
            addPlaylists(audioPlaylistFolder, (Collection<P>) audioPlaylistFolder.includedPlaylists());
        }
    }

    /**
     * Includes a {@link Collection} of {@link AudioPlaylist}s as children of a given {@link AudioPlaylistFolder} into the
     * repository, and if some the given playlists are instances of {@link AudioPlaylistFolder}, the playlists
     * included in them are added recursively too.
     *
     * @param parent    The {@link AudioPlaylistFolder} where to add the given playlists
     * @param playlists The <tt>collection</tt> of {@link AudioPlaylist}s to add to the repository under the given parent playlist
     */
    @Override
    public void addPlaylists(F parent, Collection<P> playlists) {
        playlists.forEach(includedPlaylist ->
            addPlaylist(parent, includedPlaylist));
    }

    /**
     * Removes a given {@link AudioPlaylist} from the tree hierarchy and all of its
     * included playlists if contains any.
     *
     * @param playlist  The given {@link AudioPlaylist} to remove from the repository
     */
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
    public ImmutableGraph<AudioPlaylist<I>> getPlaylistTree() {
        return ImmutableGraph.copyOf(playlistsTree);
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
