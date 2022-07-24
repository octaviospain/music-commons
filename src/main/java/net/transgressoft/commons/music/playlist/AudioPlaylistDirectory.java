package net.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class AudioPlaylistDirectory extends PlaylistNodeBase<AudioItem> {

    static final AudioPlaylistDirectory NULL = new AudioPlaylistDirectory() {

        @Override
        public void setName(String name) {
            throw new UnsupportedOperationException("Name of root AudioPlaylistDirectory cannot be modified");
        }

        @Override
        public void setAncestor(AudioPlaylistDirectory ancestor) {
            throw new UnsupportedOperationException("Ancestor of root AudioPlaylistDirectory cannot be modified");
        }

        @Override
        public void addAudioItems(List<AudioItem> audioItems) {
            throw new UnsupportedOperationException("AudioItems cannot be added to root AudioPlaylistDirectory");
        }
    };

    public static final AudioPlaylistDirectory ROOT = new AudioPlaylistDirectory(0) {

        @Override
        public void setName(String name) {
            throw new UnsupportedOperationException("Name of root AudioPlaylistDirectory cannot be modified");
        }

        @Override
        public void setAncestor(AudioPlaylistDirectory ancestor) {
            throw new UnsupportedOperationException("Ancestor of root AudioPlaylistDirectory cannot be modified");
        }

        @Override
        public void addAudioItems(List<AudioItem> audioItems) {
            throw new UnsupportedOperationException("AudioItems cannot be added to root AudioPlaylistDirectory");
        }
    };

    public static AudioPlaylistDirectoryBuilder builder(String name) {
        return new AudioPlaylistDirectoryBuilder(name);
    }

    static final AtomicInteger indexCounter = new AtomicInteger(1);

    private AudioPlaylistDirectory() {
        super();
    }

    private AudioPlaylistDirectory(int rootId) {
        super(rootId);
    }

    protected AudioPlaylistDirectory(String name, AudioPlaylistDirectory ancestor,
                                     Set<PlaylistNode<AudioItem>> descendantPlaylists, List<AudioItem> audioItems) {
        super(indexCounter.getAndIncrement(), name, ancestor, descendantPlaylists, audioItems);
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioPlaylistDirectory that = (AudioPlaylistDirectory) o;
        return Objects.equal(getName(), that.getName()) && Objects.equal(getAncestor(), that.getAncestor()) && Objects.equal(id(), that.id());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName(), getAncestor(), id());
    }

    protected static class AudioPlaylistDirectoryBuilder extends AudioPlaylist.AudioPlaylistBuilderBase<AudioPlaylistDirectory, AudioItem> {

        private Set<PlaylistNode<AudioItem>> descendantPlaylists = Collections.emptySet();

        public AudioPlaylistDirectoryBuilder(String name) {
            super(name);
        }

        public AudioPlaylistDirectoryBuilder descendantPlaylists(Set<PlaylistNode<AudioItem>> descendantPlaylists) {
            if (descendantPlaylists != null)
                this.descendantPlaylists = descendantPlaylists;
            return this;
        }

        @SafeVarargs
        public final AudioPlaylistDirectoryBuilder descendantPlaylists(PlaylistNode<AudioItem>... descendantPlaylists) {
            if (descendantPlaylists != null)
                this.descendantPlaylists = Set.of(descendantPlaylists);
            return this;
        }

        @Override
        public AudioPlaylistDirectory build() {

            return new AudioPlaylistDirectory(name, ancestor, descendantPlaylists, audioItems);
        }
    }
}
