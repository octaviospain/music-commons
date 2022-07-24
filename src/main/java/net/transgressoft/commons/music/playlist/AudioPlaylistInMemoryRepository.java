package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.InMemoryRepository;

import java.util.Collection;
import java.util.Collections;

public class AudioPlaylistInMemoryRepository extends InMemoryRepository<PlaylistNode<AudioItem>> implements AudioPlaylistRepository {

    public AudioPlaylistInMemoryRepository() {
        super(Collections.emptyList());
    }

    public AudioPlaylistInMemoryRepository(Collection<PlaylistNode<AudioItem>> playlists) {
        super(playlists);
    }

    @Override
    public <P extends PlaylistNode<A>, A extends AudioItem, D extends AudioPlaylistDirectory> void movePlaylist(P playlistToMove, D destinationPlaylist) {

    }
}
