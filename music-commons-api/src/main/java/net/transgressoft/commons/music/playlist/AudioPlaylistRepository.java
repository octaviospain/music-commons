package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.Repository;
import net.transgressoft.commons.query.RepositoryException;

import java.util.List;
import java.util.Optional;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository extends Repository<MutablePlaylistNode<AudioItem>> {

    AudioPlaylistBuilder<MutableAudioPlaylist<AudioItem>, AudioItem> createPlaylist(String name);

    AudioPlaylistDirectoryBuilder<MutablePlaylistDirectory<AudioItem>, AudioItem> createPlaylistDirectory(String name);

    List<MutablePlaylistNode<AudioItem>> findAllByName(String name);

    Optional<MutableAudioPlaylist<AudioItem>> findSinglePlaylistByName(String name) throws RepositoryException;

    Optional<MutablePlaylistDirectory<AudioItem>> findSingleDirectoryByName(String name) throws RepositoryException;

    <P extends MutablePlaylistNode<AudioItem>, D extends MutablePlaylistDirectory<AudioItem>> void movePlaylist(P playlistToMove, D destinationPlaylist) throws RepositoryException;
}
