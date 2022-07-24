package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static net.transgressoft.commons.music.playlist.AudioPlaylistDirectory.ROOT;
import static net.transgressoft.commons.music.playlist.AudioPlaylistDirectory.indexCounter;

public class AudioPlaylist extends PlaylistNodeBase<AudioItem> {

    public static AudioPlaylistBuilder builder(String name) {
        return new AudioPlaylistBuilder(name);
    }

    protected AudioPlaylist(String name, PlaylistNode<AudioItem> ancestor, Set<PlaylistNode<AudioItem>> descendantPlaylists, List<AudioItem> audioItems) {
        super(indexCounter.getAndIncrement(), name, ancestor, descendantPlaylists, audioItems);
    }

    @Override
    protected void removeAncestor(PlaylistNode<AudioItem> playlistNode) {
        playlistNode.setAncestor(ROOT);
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public void addPlaylist(PlaylistNode<AudioItem> playlist) {
        //TODO merge playlists
        throw new UnsupportedOperationException();
    }

    @Override
    public void removePlaylist(PlaylistNode<AudioItem> playlist) {
        throw new UnsupportedOperationException();
    }

    public static class AudioPlaylistBuilder implements Builder<PlaylistNode<? extends AudioItem>> {

        protected String name = "";
        protected PlaylistNode<AudioItem> ancestor = ROOT;
        protected List<AudioItem> audioItems = Collections.emptyList();

        public AudioPlaylistBuilder(String name) {
            if (name != null)
                this.name = name;
        }

        public AudioPlaylistBuilder ancestor(PlaylistNode<AudioItem> ancestor) {
            if (ancestor != null)
                this.ancestor = ancestor;
            return this;
        }

        public AudioPlaylistBuilder audioItems(List<AudioItem> audioItems) {
            if (audioItems != null)
                this.audioItems = audioItems;
            return this;
        }

        @Override
        public PlaylistNode<AudioItem> build() {
            return new AudioPlaylist(name, ancestor, Collections.emptySet(), audioItems);
        }
    }
}
