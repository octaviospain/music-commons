package net.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Base implementation of a {@code PlaylistNode}. All attributes are mutable but intend to be thread-safe, <tt>id</tt> is inmutable.
 *
 * @param <I> The type of the entities listed in the playlist node.
 */
class MutablePlaylist<I extends AudioItem> extends ImmutablePlaylist<I> implements MutableAudioPlaylist<I> {

    protected MutablePlaylist(int id, String name) {
        this(id, name, Collections.emptyList());
    }

    protected MutablePlaylist(int id, String name, List<I> audioItems) {
        super(id, name, audioItems);
    }

    @Override
    public void setName(String name) {
        requireNonNull(name);
        super.setName(name);
    }

    @Override
    public void addAudioItems(Collection<I> audioItems) {
        requireNonNull(audioItems);
        super.addAll(audioItems);
    }

    @Override
    public void removeAudioItems(Collection<I> audioItems) {
        requireNonNull(audioItems);
        super.removeAll(audioItems);
    }

    @Override
    public void clearAudioItems() {
        super.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (MutablePlaylist<I>) o;
        return Objects.equal(getName(), that.getName()) && Objects.equal(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName(), getId());
    }
}
