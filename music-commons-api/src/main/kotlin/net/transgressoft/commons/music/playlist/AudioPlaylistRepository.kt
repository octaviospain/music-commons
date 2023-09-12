package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.Repository
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.AudioItem
import java.util.*
import java.util.concurrent.Flow

/**
 * @author Octavio Calleya
 */
interface AudioPlaylistRepository<I : AudioItem, P : MutableAudioPlaylist<I>> : Repository<P, Int>, Flow.Publisher<DataEvent<P>> {

    val audioItemEventSubscriber: TransEventSubscriber<I, DataEvent<out I>>

    fun addPlaylist(playlist: AudioPlaylist<I>): P

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

    fun removeAudioItems(audioItemIds: Set<Int>)

    fun removeAudioItems(audioItems: Collection<I>)

    /**
     * Precondition, <tt>playlistToMove</tt> and <tt>destinationPlaylist</tt> exist in the <tt>AudioPlaylistRepository</tt>.
     * Otherwise, no action is performed.
     *
     * @param playlistToMove
     * @param destinationPlaylist
     */
    fun movePlaylist(playlistToMove: P, destinationPlaylist: P)

    fun findByName(name: String): Optional<out P>

    fun findParentPlaylist(playlist: P): Optional<out P>

    fun numberOfPlaylists(): Int

    fun numberOfPlaylistDirectories(): Int
}

class AudioPlaylistRepositoryException(message: String) : Exception(message)