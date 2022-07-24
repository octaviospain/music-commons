package net.transgressoft.commons.music.playlist;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

class ImmutablePlaylistDirectory<I extends AudioItem> extends ImmutablePlaylist<I> implements AudioPlaylistDirectory<I> {

    private final Set<AudioPlaylist<I>> descendantPlaylists;

    protected ImmutablePlaylistDirectory(int id, String name) {
        this(id, name, Collections.emptyList());
    }

    protected ImmutablePlaylistDirectory(int id, String name, List<I> audioItems) {
        this(id, name, audioItems, Collections.emptySet());
    }

    protected <N extends AudioPlaylist<I>> ImmutablePlaylistDirectory(int id, String name, List<I> audioItems, Set<N> playlists) {
        super(id, name, audioItems);
        descendantPlaylists= new ConcurrentSkipListSet<>(playlists);
    }

    @Override
    public <N extends AudioPlaylist<I>> boolean containsPlaylist(N playlist) {
        return descendantPlaylists.contains(playlist);
    }

    @Override
    public <N extends AudioPlaylist<I>> Set<N> descendantPlaylists() {
        return (Set<N>) descendantPlaylists;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (ImmutablePlaylistDirectory<I>) o;
        return Objects.equal(getName(), that.getName()) && Objects.equal(id(), that.id());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName(), id());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id())
                .add("name", getName())
                .add("descendantPlaylists", descendantPlaylists.size())
                .add("audioItems", audioItems().size())
                .toString();
    }
}
