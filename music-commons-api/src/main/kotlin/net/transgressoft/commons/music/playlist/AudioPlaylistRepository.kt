package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.event.QueryEntitySubscriber
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.Repository
import net.transgressoft.commons.query.RepositoryException
import java.util.*

/**
 * @author Octavio Calleya
 */
interface AudioPlaylistRepository<I : AudioItem, P : AudioPlaylist<I>, D : AudioPlaylistDirectory<I, P>> : Repository<P> {

    val audioItemEventSubscriber: QueryEntitySubscriber<I>

    @Throws(RepositoryException::class)
    fun createPlaylist(name: String): P

    @Throws(RepositoryException::class)
    fun createPlaylist(name: String, audioItems: List<I>): P

    @Throws(RepositoryException::class)
    fun createPlaylistDirectory(name: String): D

    @Throws(RepositoryException::class)
    fun createPlaylistDirectory(name: String, audioItems: List<I>): D

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
     * @param playlist
     * @param directory
     */
    fun addPlaylistsToDirectory(playlist: Set<P>, directory: D)

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
    fun movePlaylist(playlistToMove: P, destinationPlaylist: D)

    fun findAllByName(name: String): List<P>

    fun findSinglePlaylistByName(name: String): Optional<P>

    fun findSingleDirectoryByName(name: String): Optional<D>

    fun numberOfPlaylists(): Int

    fun numberOfPlaylistDirectories(): Int
}