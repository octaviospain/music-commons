package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.Repository;

import java.util.List;
import java.util.Optional;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository extends Repository<PlaylistNode<AudioItem>> {

    List<PlaylistNode<AudioItem>> findAllByName(String name);

    Optional<AudioPlaylist> findSinglePlaylistByName(String name) throws RepositoryException;

    Optional<AudioPlaylistDirectory> findSingleDirectoryByName(String name) throws RepositoryException;

    <P extends PlaylistNode<AudioItem>> void movePlaylist(P playlistToMove, P destinationPlaylist) throws RepositoryException;
}
