package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collections;
import java.util.List;

class NullAudioPlaylistNode extends DefaultMutableAudioPlaylistDirectory {

    public static final MutablePlaylistDirectory<AudioItem> INSTANCE = new NullAudioPlaylistNode();

    private NullAudioPlaylistNode() {
        super(-1, "NULL", Collections.emptySet(),  Collections.emptyList());
    }

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException("Name of NullAudioPlaylistNode cannot be modified");
    }

    @Override
    public void setAncestor(MutablePlaylistDirectory<?> ancestor) {
        throw new UnsupportedOperationException("Ancestor of NullAudioPlaylistNode cannot be modified");
    }

    @Override
    public void addAudioItems(List<AudioItem> audioItems) {
        throw new UnsupportedOperationException("AudioItems cannot be added to NullAudioPlaylistNode");
    }
}