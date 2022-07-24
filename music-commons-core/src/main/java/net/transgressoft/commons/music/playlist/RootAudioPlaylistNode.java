package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collections;
import java.util.List;

class RootAudioPlaylistNode extends DefaultMutableAudioPlaylistDirectory {

    public static final MutablePlaylistDirectory<AudioItem> INSTANCE = new RootAudioPlaylistNode();

    private RootAudioPlaylistNode() {
        super(0, "ROOT-" + RootAudioPlaylistNode.class.getClassLoader().hashCode(), NullAudioPlaylistNode.INSTANCE, Collections.emptySet(), Collections.emptyList());
    }

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException("Name of RootAudioPlaylistNode cannot be modified");
    }

    @Override
    public void setAncestor(MutablePlaylistDirectory<AudioItem> ancestor) {
        throw new UnsupportedOperationException("Ancestor of RootAudioPlaylistNode cannot be modified");
    }

    @Override
    public void addAudioItems(List<AudioItem> audioItems) {
        throw new UnsupportedOperationException("AudioItems cannot be added to RootAudioPlaylistNode");
    }
}
