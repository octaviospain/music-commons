package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository<I extends AudioItem, N extends AudioPlaylist<I>, D extends AudioPlaylistDirectory<I>>
        extends Repository<N> {

    N createPlaylist(String name);

    N createPlaylist(String name, List<I> audioItems);

    D createPlaylistDirectory(String name);

    D createPlaylistDirectory(String name, List<I> audioItems);

    /**
     * Precondition, <tt>playlist</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param playlist
     */
    void addAudioItemsToPlaylist(List<I> audioItems, N playlist);

    /**
     * Precondition, <tt>playlist</tt> and <tt>directory</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param playlist
     * @param directory
     */
    void addPlaylistsToDirectory(Set<N> playlist, D directory);

    /**
     * Precondition, <tt>playlistToMove</tt> and <tt>destinationPlaylist</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param playlistToMove
     * @param destinationPlaylist
     */
    void movePlaylist(N playlistToMove, D destinationPlaylist);

    List<N> findAllByName(String name);

    Optional<N> findSinglePlaylistByName(String name);

    Optional<D> findSingleDirectoryByName(String name);

    int numberOfPlaylists();

    int numberOfPlaylistDirectories();
}
