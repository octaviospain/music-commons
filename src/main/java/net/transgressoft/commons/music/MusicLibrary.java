package net.transgressoft.commons.music;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.playlist.AudioPlaylist;
import net.transgressoft.commons.music.playlist.PlaylistItem;
import net.transgressoft.commons.music.playlist.PlaylistTree;

import java.util.Collection;
import java.util.Set;

public interface MusicLibrary {

    MusicLibrary addAudio(Set<AudioItem> audioItems);

    Set<AudioItem> audioItems();

    MusicLibrary deleteAudio(Set<AudioItem> audioItems);

    MusicLibrary addAudioToPlaylist(Set<AudioItem> audioItems, AudioPlaylist<AudioItem> playlist);

    MusicLibrary addPlaylistToFolder(PlaylistItem<AudioItem> playlist, PlaylistTree<AudioItem> playlistFolder);

    MusicLibrary deleteAudioFromPlaylists(Set<AudioItem> audioItems, Collection<PlaylistItem<AudioItem>> playlists);
}
