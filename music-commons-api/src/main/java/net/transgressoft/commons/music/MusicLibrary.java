package net.transgressoft.commons.music;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.playlist.MutablePlaylistNode;

import java.util.Collection;
import java.util.Set;

public interface MusicLibrary {

    MusicLibrary addAudio(Set<AudioItem> audioItems);

    Set<AudioItem> audioItems();

    MusicLibrary deleteAudio(Set<AudioItem> audioItems);

    MusicLibrary addAudioToPlaylist(Set<AudioItem> audioItems, MutablePlaylistNode<AudioItem> playlist);

    MusicLibrary addPlaylistToAnother(MutablePlaylistNode<AudioItem> playlist, MutablePlaylistNode<AudioItem> playlistDirectory);

    MusicLibrary deleteAudioFromPlaylists(Set<AudioItem> audioItems, Collection<MutablePlaylistNode<AudioItem>> playlists);
}
