package com.transgressoft.commons.music.playlist;

import com.google.common.graph.ImmutableGraph;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository<I extends AudioItem, P extends AudioPlaylist<I>, F extends AudioPlaylistFolder<I>> {

    /**
     * Includes an {@link AudioPlaylist} in the repository, at the top of the hierarchy,
     * this is, at the first level of the tree, where is children of the root playlist.
     *
     * @param playlist The {@link AudioPlaylist} to include
     */
    void addToFirstLevel(P playlist);

    /**
     * Includes an {@link AudioPlaylist} as a child of a given {@link AudioPlaylistFolder} into the
     * repository. If the given {@link AudioPlaylistFolder} does not exist in the repository yet,
     * the {@link AudioPlaylistFolder} is added to the top of the hierarchy.
     * If the given {@link AudioPlaylist} is an instance of {@link AudioPlaylistFolder} too,
     * all its contained playlists are added recursively.
     *
     * @param playlistFolder The {@link AudioPlaylistFolder} of the playlist
     * @param playlist       The {@link AudioPlaylist} to add to the folder playlist
     */
    void add(P playlist, F playlistFolder);

    /**
     * Includes a {@link Collection} of {@link AudioPlaylist}s as children of a given {@link AudioPlaylistFolder} into the
     * repository, and if some the given playlists are instances of {@link AudioPlaylistFolder}, the playlists
     * included in them are added recursively too.
     *
     * @param parent    The {@link AudioPlaylistFolder} where to add the given playlists
     * @param playlists The <tt>collection</tt> of {@link AudioPlaylist}s to add to the repository under the given parent playlist
     */
    void add(F parent, Collection<P> playlists);

    /**
     * Removes a given {@link AudioPlaylist} from the tree hierarchy and all of its
     * included playlists if contains any.
     *
     * @param playlist The given {@link AudioPlaylist} to remove from the repository
     */
    void delete(P playlist);

    void move(P playlistToMove, F destinationPlaylistFolder);

    void removeAudioItems(List<I> tracks);

    boolean contains(String playlistName);

    ImmutableGraph<AudioPlaylist<I>> getPlaylistTree();

    Optional<F> getParentPlaylist(P playlist);

    boolean isParentPlaylistRoot(P playlist);

    boolean isEmpty();

    void clear();
}
