package net.transgressoft.commons.music;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.playlist.MutablePlaylistDirectory;
import net.transgressoft.commons.music.playlist.MutablePlaylistNode;

import java.util.Set;

public interface MusicLibrary {

    <I extends AudioItem> MusicLibrary addAudioItems(Set<I> audioItems);

    <I extends AudioItem> Iterable<I> audioItems();

    <I extends AudioItem> MusicLibrary deleteAudioItems(Set<I> audioItems);

    <I extends AudioItem, P extends MutablePlaylistNode<I>> MusicLibrary addAudioItemToPlaylist(I audioItems, P playlist);

    <I extends AudioItem, P extends MutablePlaylistNode<I>, D extends MutablePlaylistDirectory<I>> MusicLibrary movePlaylist(P playlist, D playlistDirectory);
}
