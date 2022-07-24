package com.transgressoft.commons.music.playlist;

import com.google.common.graph.Graph;
import com.transgressoft.commons.music.AudioItem;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository<I extends AudioItem> {

   Graph<AudioPlaylist<I>> getPlaylistsTree();

   void addPlaylist(AudioPlaylistFolder<I> parentPlaylist, AudioPlaylist<I> playlist);

   void addFirstLevelPlaylist(AudioPlaylist<I> playlist);

   void addPlaylistsRecursively(AudioPlaylistFolder<I> parent, Collection<AudioPlaylist<I>> playlists);

   void deletePlaylist(AudioPlaylist<I> playlist);

   void movePlaylist(AudioPlaylist<I> playlistToMove, AudioPlaylistFolder<I> destinationPlaylistFolder);

   void removeAudioItems(List<I> tracks);

   boolean containsPlaylist(String playlistName);

   Optional<AudioPlaylistFolder<I>> getParentPlaylist(AudioPlaylist<I> playlist);

   boolean isParentPlaylistRoot(AudioPlaylist<I> playlist);

   boolean isEmpty();

   void clear();
}
