package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.Repository
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import java.util.*
import java.util.concurrent.Flow

/**
 * @author Octavio Calleya
 */
interface AudioPlaylistRepository<I : ReactiveAudioItem<I>, P : MutableAudioPlaylist<I>> : Repository<Int, P>, Flow.Publisher<DataEvent<Int, P>> {

    val audioItemEventSubscriber: TransEventSubscriber<I, DataEvent<Int, out I>>

    @Throws(IllegalArgumentException::class)
    fun createPlaylist(name: String): P

    @Throws(IllegalArgumentException::class)
    fun createPlaylist(name: String, audioItems: List<I>): P

    @Throws(IllegalArgumentException::class)
    fun createPlaylistDirectory(name: String): P

    @Throws(IllegalArgumentException::class)
    fun createPlaylistDirectory(name: String, audioItems: List<I>): P

    fun findByName(name: String): Optional<out P>

    fun findParentPlaylist(playlist: P): Optional<out P>

    fun movePlaylist(playlistNameToMove: String, destinationPlaylistName: String)

    fun addAudioItemToPlaylist(audioItem: I, playlistName: String): Boolean = addAudioItemsToPlaylist(listOf(audioItem), playlistName)

    fun addAudioItemsToPlaylist(audioItems: Collection<I>, playlistName: String): Boolean

    fun removeAudioItemFromPlaylist(audioItem: I, playlistName: String): Boolean = removeAudioItemsFromPlaylist(listOf(audioItem), playlistName)

    fun removeAudioItemFromPlaylist(audioItemId: Int, playlistName: String): Boolean = removeAudioItemsFromPlaylist(listOf(audioItemId), playlistName)

    fun removeAudioItemsFromPlaylist(audioItems: Collection<I>, playlistName: String): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIdsFromPlaylist")
    fun removeAudioItemsFromPlaylist(audioItemIds: Collection<Int>, playlistName: String): Boolean

    fun addPlaylistToDirectory(playlistToAdd: P, directoryName: String): Boolean = addPlaylistsToDirectory(setOf(playlistToAdd), directoryName)

    fun addPlaylistToDirectory(playlistNameToAdd: String, directoryName: String): Boolean = addPlaylistsToDirectory(setOf(playlistNameToAdd), directoryName)

    fun addPlaylistsToDirectory(playlistsToAdd: Set<P>, directoryName: String): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addPlaylistNamesToDirectory")
    fun addPlaylistsToDirectory(playlistNamesToAdd: Set<String>, directoryName: String): Boolean

    fun removePlaylistFromDirectory(playlistToRemove: P, directoryName: String): Boolean = removePlaylistsFromDirectory(setOf(playlistToRemove), directoryName)

    fun removePlaylistFromDirectory(playlistNameToRemove: String, directoryName: String): Boolean = removePlaylistsFromDirectory(setOf(playlistNameToRemove), directoryName)

    fun removePlaylistsFromDirectory(playlistsToRemove: Set<P>, directoryName: String): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistNamesFromDirectory")
    fun removePlaylistsFromDirectory(playlistsNamesToRemove: Set<String>, directoryName: String): Boolean

    fun numberOfPlaylists(): Int

    fun numberOfPlaylistDirectories(): Int
}
