package com.transgressoft.commons.music.playlist;

import com.google.common.graph.ImmutableGraph;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository<I extends AudioItem, P extends AudioPlaylist<I>, F extends AudioPlaylistFolder<I>> {

   void addFirstLevelPlaylist(P playlist);

   void addPlaylist(F parentPlaylist, P playlist);

   void addPlaylists(F parent, Collection<P> playlists);

   void deletePlaylist(P playlist);

   void movePlaylist(P playlistToMove, F destinationPlaylistFolder);

   void removeAudioItems(List<I> tracks);

   boolean containsPlaylist(String playlistName);

   ImmutableGraph<AudioPlaylist<I>> getPlaylistTree();

   Optional<F> getParentPlaylist(P playlist);

   boolean isParentPlaylistRoot(P playlist);

   boolean isEmpty();

   void clear();
}
