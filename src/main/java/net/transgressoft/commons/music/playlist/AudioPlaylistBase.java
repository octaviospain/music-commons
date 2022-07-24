package net.transgressoft.commons.music.playlist;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import net.transgressoft.commons.music.audio.AudioItem;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Octavio Calleya
 */
public abstract class AudioPlaylistBase<I extends AudioItem> implements AudioPlaylist<I> {

    private final String name;
    private final List<I> audioItems;

    protected AudioPlaylistBase(String name, List<I> audioItems) {
        this.name = name;
        this.audioItems = new ArrayList<>(audioItems);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ImmutableList<I> audioItems() {
        return ImmutableList.copyOf(audioItems);
    }

    @Override
    public boolean isEmpty() {
        return audioItems.isEmpty();
    }

    @SuppressWarnings ("unchecked")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioPlaylist<I> that = (AudioPlaylist<I>) o;
        return Objects.equal(name, that.name());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .toString();
    }
}
