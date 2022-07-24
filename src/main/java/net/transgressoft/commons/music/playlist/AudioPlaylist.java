package net.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
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

    protected AudioPlaylist(String name, AudioPlaylistDirectory ancestor, Set<PlaylistNode<AudioItem>> descendantPlaylists, List<AudioItem> audioItems) {
        super(indexCounter.getAndIncrement(), name, ancestor, descendantPlaylists, audioItems);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioPlaylist that = (AudioPlaylist) o;
        return Objects.equal(getName(), that.getName()) && Objects.equal(getAncestor(), that.getAncestor()) && Objects.equal(id(), that.id());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName(), getAncestor(), id());
    }

    protected abstract static class AudioPlaylistBuilderBase<P extends PlaylistNode<I>, I extends AudioItem> implements Builder<P, I> {

        protected String name = "";
        protected AudioPlaylistDirectory ancestor = ROOT;
        protected List<AudioItem> audioItems = Collections.emptyList();

        protected AudioPlaylistBuilderBase(String name) {
            if (name != null)
                this.name = name;
        }

        public Builder<P, I> ancestor(AudioPlaylistDirectory ancestor) {
            if (ancestor != null)
                this.ancestor = ancestor;
            return this;
        }

        public Builder<P, I> audioItems(List<AudioItem> audioItems) {
            if (audioItems != null)
                this.audioItems = audioItems;
            return this;
        }
    }

    protected static class AudioPlaylistBuilder extends AudioPlaylistBuilderBase<AudioPlaylist, AudioItem> {

        public AudioPlaylistBuilder(String name) {
            super(name);
        }

        @Override
        public AudioPlaylist build() {
            return new AudioPlaylist(name, ancestor, Collections.emptySet(), audioItems);
        }
    }
}
