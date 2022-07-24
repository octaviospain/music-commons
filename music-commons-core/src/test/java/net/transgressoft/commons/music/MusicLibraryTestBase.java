package net.transgressoft.commons.music;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.audio.AudioItemTestFactory;
import net.transgressoft.commons.music.playlist.AudioPlaylistTestFactory;
import net.transgressoft.commons.music.playlist.MutablePlaylistDirectory;
import net.transgressoft.commons.music.playlist.MutablePlaylistNode;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public class MusicLibraryTestBase {

    AudioItemTestFactory audioItemTestFactory = new AudioItemTestFactory();
    AudioPlaylistTestFactory audioPlaylistTestFactory = new AudioPlaylistTestFactory();

    protected MutablePlaylistNode<AudioItem> createPlaylist(String name) {
        return audioPlaylistTestFactory.createPlaylist(name);
    }

    protected MutablePlaylistNode<AudioItem> createPlaylist(String name, MutablePlaylistDirectory<AudioItem> ancestor) {
        return audioPlaylistTestFactory.createPlaylist(name, ancestor);
    }

    protected MutablePlaylistNode<AudioItem> createPlaylist(String name, List<AudioItem> audioItems) {
        return audioPlaylistTestFactory.createPlaylist(name, audioItems);
    }

    protected MutablePlaylistDirectory<AudioItem> createPlaylistDirectory(String name) {
        return audioPlaylistTestFactory.createPlaylistDirectory(name);
    }

    protected MutablePlaylistDirectory<AudioItem> createPlaylistDirectory(String name, List<AudioItem> audioItems) {
        return audioPlaylistTestFactory.createPlaylistDirectory(name, audioItems);
    }

    protected MutablePlaylistDirectory<AudioItem> createPlaylistDirectory(String name, Set<MutablePlaylistNode<AudioItem>> playlists) {
        return audioPlaylistTestFactory.createPlaylistDirectory(name, playlists);
    }

    protected MutablePlaylistDirectory<AudioItem> createPlaylistDirectory(String name, Set<MutablePlaylistNode<AudioItem>> playlists,
                                                                        List<AudioItem> audioItems) {
        return audioPlaylistTestFactory.createPlaylistDirectory(name, playlists, audioItems);
    }

    protected AudioItem createTestAudioItem() {
        return audioItemTestFactory.createTestAudioItem();
    }

    protected AudioItem createTestAudioItem(String path, String name, Duration duration) {
        return audioItemTestFactory.createTestAudioItem(path, name, duration);
    }

    protected List<AudioItem> createTestAudioItemsSet(int size) {
        return audioItemTestFactory.createTestAudioItemsList(size);
    }
}
