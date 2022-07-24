package net.transgressoft.commons.music;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.audio.AudioItemInMemoryRepository;
import net.transgressoft.commons.music.audio.AudioItemRepository;
import net.transgressoft.commons.music.playlist.AudioPlaylistInMemoryRepository;
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository;
import net.transgressoft.commons.music.playlist.MutablePlaylistDirectory;
import net.transgressoft.commons.music.playlist.MutablePlaylistNode;

import java.util.Set;

public class DefaultMusicLibrary implements MusicLibrary {

    private final AudioItemRepository audioItemRepository = new AudioItemInMemoryRepository();
    private final AudioPlaylistRepository audioPlaylistRepository = new AudioPlaylistInMemoryRepository();

    public DefaultMusicLibrary() {

    }

    @Override
    public <I extends AudioItem> MusicLibrary addAudioItems(Set<I> audioItems) {
        return null;
    }

    @Override
    public <I extends AudioItem> Iterable<I> audioItems() {
        return null;
    }

    @Override
    public <I extends AudioItem> MusicLibrary deleteAudioItems(Set<I> audioItems) {
        return null;
    }

    @Override
    public <I extends AudioItem, P extends MutablePlaylistNode<I>> MusicLibrary addAudioItemToPlaylist(I audioItems, P playlist) {
        return null;
    }

    @Override
    public <I extends AudioItem, P extends MutablePlaylistNode<I>, D extends MutablePlaylistDirectory<I>> MusicLibrary movePlaylist(P playlist, D playlistDirectory) {
        return null;
    }
}
