package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Octavio Calleya
 */
public class ImmutableAudioPlaylist extends AudioPlaylistBase<AudioItem> {

    public ImmutableAudioPlaylist(String name, List<AudioItem> audioItems) {
        super(name, audioItems);
    }

    public ImmutableAudioPlaylist(String name) {
        super(name, Collections.emptyList());
    }

    @Override
    public PlaylistItem<AudioItem> name(String name) {
        return new ImmutableAudioPlaylist(name, audioItems());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <P extends PlaylistItem<AudioItem>> P removeAudioItems(Set<AudioItem> audioItems) {
        List<AudioItem> list = new ArrayList<>(audioItems());
        list.removeAll(audioItems);
        return (P) new ImmutableAudioPlaylist(name(), list);
    }

    @Override
    public AudioPlaylist<AudioItem> addAudioItems(List<AudioItem> audioItems) {
        List<AudioItem> list = new ArrayList<>(audioItems);
        list.addAll(audioItems());
        return new ImmutableAudioPlaylist(name(), list);
    }

    @Override
    public AudioPlaylist<AudioItem> clear() {
        return new ImmutableAudioPlaylist(name(), Collections.emptyList());
    }
}
