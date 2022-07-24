package net.transgressoft.commons.music.playlist;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

class MutablePlaylistDirectory<I extends AudioItem> extends ImmutablePlaylistDirectory<I> implements MutableAudioPlaylistDirectory<I> {

    protected MutablePlaylistDirectory(int id, String name) {
        this(id, name, Collections.emptyList());
    }

    protected MutablePlaylistDirectory(int id, String name, List<I> audioItems) {
        this(id, name, audioItems, Collections.emptySet());
    }

    protected <N extends AudioPlaylist<I>> MutablePlaylistDirectory(int id, String name, List<I> audioItems, Set<N> playlists) {
        super(id, name, audioItems, playlists);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public void addAudioItems(Collection<I> audioItems) {
        super.addAll(audioItems);
    }

    @Override
    public void removeAudioItems(Collection<I> audioItems) {
        super.removeAll(audioItems);
    }

    @Override
    public void clearAudioItems() {
        super.clear();
    }

    @Override
    public <N extends AudioPlaylist<I>> void addPlaylists(N... playlists) {
        requireNonNull(playlists);
        addPlaylists(Set.of(playlists));
    }

    @Override
    public <N extends AudioPlaylist<I>> void addPlaylists(Set<N> playlists) {
        requireNonNull(playlists);
        super.addAll(playlists);
    }

    @Override
    public <N extends AudioPlaylist<I>> void removePlaylists(N... playlists) {
        requireNonNull(playlists);
        removePlaylists(Set.of(playlists));
    }

    @Override
    public <N extends AudioPlaylist<I>> void removePlaylists(Set<N> playlists) {
        requireNonNull(playlists);
        super.removeAll(playlists);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (MutablePlaylistDirectory<I>) o;
        return Objects.equal(getName(), that.getName()) && Objects.equal(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName(), getId());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", getId())
                .add("name", getName())
                .add("descendantPlaylists", descendantPlaylists().size())
                .add("audioItems", audioItems().size())
                .toString();
    }
}
