package net.transgressoft.commons.music.playlist;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import net.transgressoft.commons.music.audio.AudioItem;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static java.util.Objects.requireNonNull;

class DefaultMutableAudioPlaylistDirectory extends MutablePlaylistNodeBase<AudioItem> implements MutablePlaylistDirectory<AudioItem> {

    private final Set<MutablePlaylistNode<AudioItem>> descendantPlaylists;

    protected DefaultMutableAudioPlaylistDirectory(int id, String name, MutablePlaylistDirectory<AudioItem> ancestor,
                                                   Set<MutablePlaylistNode<AudioItem>> descendantPlaylists, List<AudioItem> audioItems) {
        super(id, name, ancestor, audioItems);
        this.descendantPlaylists = new ConcurrentSkipListSet<>(descendantPlaylists);
        descendantPlaylists.forEach(p -> p.setAncestor(this));
    }

    DefaultMutableAudioPlaylistDirectory(int id, String name, Set<MutablePlaylistNode<AudioItem>> descendantPlaylists, List<AudioItem> audioItems) {
        super(id, name, audioItems);
        this.descendantPlaylists = new ConcurrentSkipListSet<>(descendantPlaylists);
        descendantPlaylists.forEach(p -> p.setAncestor(this));
    }

    @Override
    public <P extends MutablePlaylistNode<AudioItem>> void addPlaylist(P playlist) {
        requireNonNull(playlist);
        if (isDirectory()) {
            descendantPlaylists.add(playlist);
            playlist.setAncestor(this);
        }
    }

    @Override
    public <P extends MutablePlaylistNode<AudioItem>> void removePlaylist(P playlist) {
        var iterator = descendantPlaylists.iterator();
        while (iterator.hasNext()) {
            var p = iterator.next();
            if (p.equals(playlist)) {
                p.setAncestor(null);
                iterator.remove();
            }
        }
    }

    @Override
    public <P extends MutablePlaylistNode<AudioItem>> ListIterator<P> descendantPlaylistsIterator() {
        return (ListIterator<P>) ImmutableList.copyOf(descendantPlaylists).listIterator();
    }

    @Override
    public void clearDescendantPlaylists() {
        descendantPlaylists.clear();
    }

    @Override
    public boolean isEmptyOfPlaylists() {
        return descendantPlaylists.isEmpty();
    }

    @Override
    public void clearAudioItemsFromPlaylists() {
        descendantPlaylists.forEach(MutablePlaylistNode::clearAudioItems);
    }

    @Override
    public <P extends MutablePlaylistNode<AudioItem>> boolean containsPlaylist(P playlist) {
        return descendantPlaylists.contains(playlist);
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultMutableAudioPlaylistDirectory that = (DefaultMutableAudioPlaylistDirectory) o;
        return Objects.equal(getName(), that.getName()) && Objects.equal(getAncestor(), that.getAncestor()) && Objects.equal(id(), that.id());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName(), getAncestor(), id());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id())
                .add("name", getName())
                .add("ancestor", "{id=" + getAncestor().id() + "}")
                .add("descendantPlaylists", descendantPlaylists.size())
                .add("audioItems", numberOfAudioItems())
                .toString();
    }
}
