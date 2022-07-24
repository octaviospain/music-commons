package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.Collections;
import java.util.Set;

class MutableAudioPlaylistDirectoryBuilder extends AudioPlaylistBuilderBase<MutablePlaylistDirectory<AudioItem>, AudioItem>
        implements AudioPlaylistDirectoryBuilder<MutablePlaylistDirectory<AudioItem>, AudioItem> {

    private Set<MutablePlaylistNode<AudioItem>> descendantPlaylists = Collections.emptySet();

    MutableAudioPlaylistDirectoryBuilder(int id, String name) {
        super(id, name);
    }

    @Override
    public MutableAudioPlaylistDirectoryBuilder descendantPlaylists(Set<MutablePlaylistNode<AudioItem>> descendantPlaylists) {
        if (descendantPlaylists != null)
            this.descendantPlaylists = descendantPlaylists;
        return this;
    }

    @Override
    public MutablePlaylistDirectory<AudioItem> build() {
        return new DefaultMutableAudioPlaylistDirectory(id, name, ancestor, descendantPlaylists, audioItems);
    }
}
