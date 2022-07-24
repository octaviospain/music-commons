package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collection;
import java.util.List;

interface MutableAudioPlaylist<I extends AudioItem> extends AudioPlaylist<I> {

    void setName(String name);

    void addAudioItems(List<I> audioItems);

    void removeAudioItems(Collection<I> audioItems);

    void clearAudioItems();
}
