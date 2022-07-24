package net.transgressoft.commons.music;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.audio.AudioItemInMemoryRepository;
import net.transgressoft.commons.music.audio.AudioItemRepository;
import net.transgressoft.commons.music.playlist.AudioPlaylist;
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory;
import net.transgressoft.commons.music.playlist.AudioPlaylistInMemoryRepository;
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository;

import java.util.Set;

public class DefaultMusicLibrary implements MusicLibrary {

    private final AudioItemRepository audioItemRepository = new AudioItemInMemoryRepository();
    private final AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>> audioPlaylistRepository = new AudioPlaylistInMemoryRepository<>();

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
    public <I extends AudioItem, P extends AudioPlaylist<I>> MusicLibrary addAudioItemToPlaylist(I audioItems, P playlist) {
        return null;
    }

    @Override
    public <I extends AudioItem, P extends AudioPlaylist<I>, D extends AudioPlaylistDirectory<I>> MusicLibrary movePlaylist(P playlist, D playlistDirectory) {
        return null;
    }
}
