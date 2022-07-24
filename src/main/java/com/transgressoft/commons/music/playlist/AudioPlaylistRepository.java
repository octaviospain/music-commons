package com.transgressoft.commons.music.playlist;

import com.google.common.graph.Graph;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository<P extends AudioPlaylist> {

   Graph<P> getPlaylistsTree();

   void addPlaylist(P parentPlaylist, P playlist);

   void addPlaylistToRoot(P playlist);

   void addPlaylistsRecursively(P parent, Collection<P> playlists);

   void deletePlaylist(P playlist);

   void movePlaylist(P movedPlaylist, P targetFolder);

   void removeAudioItems(Collection<? extends AudioItem> tracks);

   boolean containsPlaylist(String playlistName);

   Optional<P> getParentPlaylist(P playlist);

   boolean isParentPlaylistRoot(P playlist);

   boolean isEmpty();

   void clear();
}
