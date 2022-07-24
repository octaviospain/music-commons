package net.transgressoft.commons.music;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.playlist.PlaylistNode;

import java.util.Collection;
import java.util.Set;

public interface MusicLibrary {

    MusicLibrary addAudio(Set<AudioItem> audioItems);

    Set<AudioItem> audioItems();

    MusicLibrary deleteAudio(Set<AudioItem> audioItems);

    MusicLibrary addAudioToPlaylist(Set<AudioItem> audioItems, PlaylistNode<AudioItem> playlist);

    MusicLibrary addPlaylistToAnother(PlaylistNode<AudioItem> playlist, PlaylistNode<AudioItem> playlistDirectory);

    MusicLibrary deleteAudioFromPlaylists(Set<AudioItem> audioItems, Collection<PlaylistNode<AudioItem>> playlists);
}
