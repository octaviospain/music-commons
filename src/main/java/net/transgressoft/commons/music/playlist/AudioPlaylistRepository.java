package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.Repository;

/**
 * @author Octavio Calleya
 */
public interface AudioPlaylistRepository extends Repository<PlaylistNode<AudioItem>> {

    <P extends PlaylistNode<A>, A extends AudioItem, D extends AudioPlaylistDirectory> void movePlaylist(P playlistToMove, D destinationPlaylist);
}
