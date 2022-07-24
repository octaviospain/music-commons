package net.transgressoft.commons.music.playlist;

import com.google.common.collect.ImmutableCollection;
import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Set;

public interface PlaylistItem<I extends AudioItem> {

    String name();

    PlaylistItem<I> name(String name);

    <P extends PlaylistItem<I>> P removeAudioItems(Set<AudioItem> audioItems);

    ImmutableCollection<I> audioItems();
}
