package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.AudioItem;

import java.util.Collections;
import java.util.List;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioPlaylist extends AudioPlaylistBase<AudioItem> {

    public SimpleAudioPlaylist(String name, List<AudioItem> audioItems) {
        super(name, audioItems);
    }

    public SimpleAudioPlaylist(String name) {
        super(name, Collections.emptyList());
    }
}
