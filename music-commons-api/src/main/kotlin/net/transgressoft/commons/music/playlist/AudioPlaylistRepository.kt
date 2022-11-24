package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.event.EntityEvent
import net.transgressoft.commons.event.QueryEntitySubscriber
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.Repository
import java.util.*
import java.util.concurrent.Flow

/**
 * @author Octavio Calleya
 */
interface AudioPlaylistRepository<I : AudioItem, P : AudioPlaylist<I>> : Repository<P>, Flow.Publisher<EntityEvent<out P>> {

    val audioItemEventSubscriber: QueryEntitySubscriber<I>

    @Throws(AudioPlaylistRepositoryException::class)
    fun createPlaylist(name: String): P

    @Throws(AudioPlaylistRepositoryException::class)
    fun createPlaylist(name: String, audioItems: List<I>): P

    @Throws(AudioPlaylistRepositoryException::class)
    fun createPlaylistDirectory(name: String): P

    @Throws(AudioPlaylistRepositoryException::class)
    fun createPlaylistDirectory(name: String, audioItems: List<I>): P

    /**
     * Precondition, <tt>playlist</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param audioItems
     * @param playlist
     */
    fun addAudioItemsToPlaylist(audioItems: Collection<I>, playlist: P)

    /**
     * Precondition, <tt>playlist</tt> and <tt>directory</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param playlistsToAdd
     * @param directory
     */
    @Throws(AudioPlaylistRepositoryException::class)
    fun addPlaylistsToDirectory(playlistsToAdd: Set<P>, directory: P)

    /**
     * Precondition, <tt>playlist</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param audioItems
     * @param playlist
     */
    fun removeAudioItemsFromPlaylist(audioItems: Collection<I>, playlist: P)

    fun removeAudioItems(audioItems: Collection<I>)

    /**
     * Precondition, <tt>playlistToMove</tt> and <tt>destinationPlaylist</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param playlistToMove
     * @param destinationPlaylist
     */
    @Throws(AudioPlaylistRepositoryException::class)
    fun movePlaylist(playlistToMove: P, destinationPlaylist: P)

    fun findByName(name: String): P?

    fun numberOfPlaylists(): Int

    fun numberOfPlaylistDirectories(): Int
}

class AudioPlaylistRepositoryException(message: String) : Exception(message)