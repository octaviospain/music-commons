package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.AudioItem;

/**
 * @author Octavio Calleya
 */
public abstract class AudioPlaylistRepositoryBase<I extends AudioItem, P extends AudioPlaylist<I>, T extends PlaylistTree<I>>
        implements AudioPlaylistRepository<I, P, T> {

    private final T playlistTree;

    @SuppressWarnings ("unchecked")
    protected AudioPlaylistRepositoryBase() {
        this((T) new SimplePlaylistTree("ROOT_PLAYLIST"));
    }

    protected AudioPlaylistRepositoryBase(T playlistTree) {
        this.playlistTree = playlistTree;
    }

    @Override
    public T getRootPlaylistTree() {
        return playlistTree;
    }

}
