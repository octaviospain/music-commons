package net.transgressoft.commons.music.playlist;

import com.google.common.base.Objects;
import net.transgressoft.commons.music.audio.AudioItem;

import java.util.List;

class DefaultMutableAudioPlaylist extends MutablePlaylistNodeBase<AudioItem> implements MutableAudioPlaylist<AudioItem> {

    protected DefaultMutableAudioPlaylist(int id, String name, MutablePlaylistDirectory<AudioItem> ancestor, List<AudioItem> audioItems) {
        super(id, name, ancestor, audioItems);
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultMutableAudioPlaylist that = (DefaultMutableAudioPlaylist) o;
        return Objects.equal(getName(), that.getName()) && Objects.equal(getAncestor(), that.getAncestor()) && Objects.equal(id(), that.id());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName(), getAncestor(), id());
    }
}
