package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collections;
import java.util.List;

abstract class AudioPlaylistBuilderBase<P extends MutablePlaylistNode<I>, I extends AudioItem> implements AudioPlaylistBuilder<P, I> {

    protected int id;
    protected String name = "";
    protected MutablePlaylistDirectory<I> ancestor = (MutablePlaylistDirectory<I>) RootAudioPlaylistNode.INSTANCE;
    protected List<I> audioItems = Collections.emptyList();

    protected AudioPlaylistBuilderBase(int id, String name) {
        this.id = id;
        if (name != null)
            this.name = name;
    }

    @Override
    public AudioPlaylistBuilder<P, I> ancestor(MutablePlaylistDirectory<I> ancestor) {
        if (ancestor != null)
            this.ancestor = ancestor;
        return this;
    }

    @Override
    public AudioPlaylistBuilder<P, I> audioItems(List<I> audioItems) {
        if (audioItems != null)
            this.audioItems = audioItems;
        return this;
    }
}
