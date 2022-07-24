package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.List;
import java.util.Set;

public class AudioPlaylistTestFactory {

    private int testCounter = 1;

    public MutablePlaylistNode<AudioItem> createPlaylist(String name) {
        return new MutableAudioPlaylistBuilder(testCounter++, name)
                .build();
    }

    public MutablePlaylistNode<AudioItem> createPlaylist(String name, MutablePlaylistDirectory<AudioItem> ancestor) {
        return new MutableAudioPlaylistBuilder(testCounter++, name)
                .ancestor(ancestor)
                .build();
    }

    public MutablePlaylistNode<AudioItem> createPlaylist(String name, List<AudioItem> audioItems) {
        return new MutableAudioPlaylistBuilder(testCounter++, name)
                .audioItems(audioItems)
                .build();
    }

    public MutablePlaylistDirectory<AudioItem> createPlaylistDirectory(String name) {
        return new MutableAudioPlaylistDirectoryBuilder(testCounter++, name)
                .build();
    }

    public MutablePlaylistDirectory<AudioItem> createPlaylistDirectory(String name, Set<MutablePlaylistNode<AudioItem>> playlists) {
        return new MutableAudioPlaylistDirectoryBuilder(testCounter++, name)
                .descendantPlaylists(playlists)
                .build();
    }

    public MutablePlaylistDirectory<AudioItem> createPlaylistDirectory(String name, List<AudioItem> audioItems) {
        return new MutableAudioPlaylistDirectoryBuilder(testCounter++, name)
                .audioItems(audioItems)
                .build();
    }

    public MutablePlaylistDirectory<AudioItem> createPlaylistDirectory(String name, Set<MutablePlaylistNode<AudioItem>> playlists,
                                                                  List<AudioItem> audioItems) {
        return new MutableAudioPlaylistDirectoryBuilder(testCounter++, name)
                .descendantPlaylists(playlists)
                .audioItems(audioItems)
                .build();
    }
}
