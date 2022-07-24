package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AudioPlaylistInMemoryRepository extends AudioPlaylistInMemoryRepositoryBase<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>,
        MutableAudioPlaylist<AudioItem>, MutableAudioPlaylistDirectory<AudioItem>> {

    public AudioPlaylistInMemoryRepository() {
        this(new HashMap<>(), new HashMap<>());
    }

    public AudioPlaylistInMemoryRepository(Map<Integer, MutableAudioPlaylist<AudioItem>> playlistsById, Map<Integer, MutableAudioPlaylistDirectory<AudioItem>> directoriesById) {
        super(playlistsById, directoriesById);
    }

    @Override
    protected MutableAudioPlaylist<AudioItem> toMutablePlaylist(AudioPlaylist<AudioItem> playlistDirectory) {
        return new MutablePlaylist<>(getNewId(), playlistDirectory.getName(), playlistDirectory.audioItems());
    }

    @Override
    protected Set<MutableAudioPlaylist<AudioItem>> toMutablePlaylists(Set<AudioPlaylist<AudioItem>> audioPlaylists) {
        return audioPlaylists.stream()
                .map(e -> {
                    if (e.isDirectory()) {
                        AudioPlaylistDirectory<AudioItem> dir = (AudioPlaylistDirectory<AudioItem>) e;
                        return new MutablePlaylistDirectory<>(dir.getId(), dir.getName(), dir.audioItems(), dir.descendantPlaylists());
                    } else {
                        return new MutablePlaylist<>(e.getId(), e.getName(), e.audioItems());
                    }
                })
                .collect(Collectors.toSet());
    }

    @Override
    protected MutableAudioPlaylistDirectory<AudioItem> toMutableDirectory(AudioPlaylistDirectory<AudioItem> playlistDirectory) {
        return new MutablePlaylistDirectory<>(getNewId(), playlistDirectory.getName(), playlistDirectory.audioItems(), toMutablePlaylists(playlistDirectory.descendantPlaylists()));
    }

    @Override
    protected AudioPlaylist<AudioItem> toImmutablePlaylist(MutableAudioPlaylist<AudioItem> audioPlaylist) {
        return new ImmutablePlaylist<>(audioPlaylist.getId(), audioPlaylist.getName(), audioPlaylist.audioItems());
    }

    @Override
    protected AudioPlaylist<AudioItem> toImmutablePlaylist(AudioPlaylist<AudioItem> audioPlaylist) {
        return new ImmutablePlaylist<>(audioPlaylist.getId(), audioPlaylist.getName(), audioPlaylist.audioItems());
    }

    @Override
    protected AudioPlaylist<AudioItem> toImmutablePlaylistDirectory(MutableAudioPlaylistDirectory<AudioItem> playlistDirectory) {
        return playlistDirectory == null ? null :
                new ImmutablePlaylistDirectory<>(playlistDirectory.getId(), playlistDirectory.getName(), playlistDirectory.audioItems(),
                                                 toImmutablePlaylistDirectories(playlistDirectory.descendantPlaylists()));
    }

    @Override
    protected Set<AudioPlaylist<AudioItem>> toImmutablePlaylistDirectories(Set<AudioPlaylist<AudioItem>> audioPlaylists) {
        return audioPlaylists.stream()
                .map(p -> p.isDirectory() ? toImmutablePlaylistDirectory((MutableAudioPlaylistDirectory<AudioItem>) p) : toImmutablePlaylist(p))
                .collect(Collectors.toSet());
    }
}
