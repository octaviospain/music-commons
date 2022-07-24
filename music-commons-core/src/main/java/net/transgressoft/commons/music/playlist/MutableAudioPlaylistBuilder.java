package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

class MutableAudioPlaylistBuilder extends AudioPlaylistBuilderBase<MutableAudioPlaylist<AudioItem>, AudioItem> implements AudioPlaylistBuilder<MutableAudioPlaylist<AudioItem>, AudioItem> {

    protected MutableAudioPlaylistBuilder(int id, String name) {
        super(id, name);
    }

    @Override
    public MutableAudioPlaylist<AudioItem> build() {
        return new DefaultMutableAudioPlaylist(id, name, ancestor, audioItems);
    }
}
