package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class AudioPlaylistDirectory extends PlaylistNodeBase<AudioItem> {

    public static final AudioPlaylistDirectory ROOT = new AudioPlaylistDirectory(0) {

        @Override
        public void setName(String name) {
            throw new UnsupportedOperationException("Name of root AudioPlaylistDirectory cannot be modified");
        }

        @Override
        public void setAncestor(PlaylistNode<AudioItem> ancestor) {
            throw new UnsupportedOperationException("Ancestor of root AudioPlaylistDirectory cannot be modified");
        }

        @Override
        public void addAudioItems(List<AudioItem> audioItems) {
            throw new UnsupportedOperationException("AudioItems cannot be added to of root AudioPlaylistDirectory");
        }
    };

    public static AudioPlaylistDirectoryBuilder builder(String name) {
        return new AudioPlaylistDirectoryBuilder(name);
    }

    static final AtomicInteger indexCounter = new AtomicInteger(1);

    private AudioPlaylistDirectory(int rootId) {
        super(rootId);
    }

    protected AudioPlaylistDirectory(String name, PlaylistNode<AudioItem> ancestor,
                                     Set<PlaylistNode<AudioItem>> descendantPlaylists, List<AudioItem> audioItems) {
        super(indexCounter.getAndIncrement(), name, ancestor, descendantPlaylists, audioItems);
    }

    @Override
    protected void removeAncestor(PlaylistNode<AudioItem> playlistNode) {
        playlistNode.setAncestor(ROOT);
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    public static class AudioPlaylistDirectoryBuilder extends AudioPlaylist.AudioPlaylistBuilder {

        private Set<PlaylistNode<AudioItem>> descendantPlaylists = Collections.emptySet();

        public AudioPlaylistDirectoryBuilder(String name) {
            super(name);
        }

        public AudioPlaylistDirectoryBuilder descendantPlaylists(Set<PlaylistNode<AudioItem>> descendantPlaylists) {
            if (descendantPlaylists != null)
                this.descendantPlaylists = descendantPlaylists;
            return this;
        }

        @Override
        public PlaylistNode<AudioItem> build() {
            return new AudioPlaylistDirectory(name, ancestor, descendantPlaylists, audioItems);
        }
    }
}
