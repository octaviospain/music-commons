package com.transgressoft.commons.music.repository;

import com.google.common.graph.Graph;
import com.transgressoft.commons.music.AudioItem;
import com.transgressoft.commons.music.playlist.AudioPlaylist;

import java.util.*;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository {

   Graph<AudioPlaylist> getPlaylistsTree();

   void addPlaylist(AudioPlaylist parentPlaylist, AudioPlaylist playlist);

   void addPlaylistToRoot(AudioPlaylist playlist);

   void addPlaylistsRecursively(AudioPlaylist parent, Collection<? extends AudioPlaylist> playlists);

   void deletePlaylist(AudioPlaylist playlist);

   void movePlaylist(AudioPlaylist movedPlaylist, AudioPlaylist targetFolder);

   void removeAudioItems(Collection<? extends AudioItem> tracks);

   boolean containsPlaylist(String playlistName);

   Optional<AudioPlaylist> getParentPlaylist(AudioPlaylist playlist);

   boolean isEmpty();

   void clear();
}
