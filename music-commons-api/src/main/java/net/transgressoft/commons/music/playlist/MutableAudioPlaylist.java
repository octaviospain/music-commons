package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collection;

interface MutableAudioPlaylist<I extends AudioItem> extends AudioPlaylist<I> {

    void setName(String name);

    void addAudioItems(Collection<I> audioItems);

    void removeAudioItems(Collection<I> audioItems);

    void clearAudioItems();
}
