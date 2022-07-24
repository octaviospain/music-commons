package net.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableSet;
import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Optional;

/**
 * Represents a data object that contains a collection of {@link AudioItem}s and also
 * a collection of {@link PlaylistTree} itself.
 *
 * @author Octavio Calleya
 */
public interface PlaylistTree<I extends AudioItem> extends PlaylistItem<I> {

    PlaylistTree<I> addPlaylist(AudioPlaylist<I> playlist);

    PlaylistTree<I> removeAudioPlaylist(AudioPlaylist<I> playlist);

    PlaylistTree<I> addPlaylistTree(PlaylistTree<I> playlistTree);

    ImmutableSet<AudioPlaylist<I>> audioPlaylists();

    ImmutableSet<PlaylistTree<I>> includedPlaylistTrees();

    PlaylistTree<I> removePlaylistTree(PlaylistTree<I> playlistTree);

    Optional<PlaylistTree<I>> findParentPlaylist(String playlistName);

    Optional<AudioPlaylist<I>> findAudioPlaylistByName(String playlistName);

    Optional<PlaylistTree<I>> findPlaylistTreeByName(String playlistName);

    PlaylistTree<I> clearIncludedPlaylistTrees();

    PlaylistTree<I> clearPlaylists();
}
