package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.ReactiveEntity
import net.transgressoft.commons.music.audio.ReactiveAudioItem

interface ReactiveAudioPlaylist<I : ReactiveAudioItem<I>, P : ReactiveAudioPlaylist<I, P>> : AudioPlaylist<I>, ReactiveEntity<Int, P> {

    override var name: String

    override var isDirectory: Boolean

    fun addAudioItem(audioItem: I): Boolean = addAudioItems(listOf(audioItem))

    fun addAudioItems(audioItems: Collection<I>): Boolean

    fun removeAudioItem(audioItem: I): Boolean = removeAudioItems(listOf(audioItem))

    fun removeAudioItem(audioItemId: Int): Boolean = removeAudioItems(listOf(audioItemId))

    fun removeAudioItems(audioItems: Collection<I>): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIds")
    fun removeAudioItems(audioItemIds: Collection<Int>): Boolean

    fun addPlaylist(playlist: P): Boolean = addPlaylists(listOf(playlist))

    fun addPlaylists(playlists: Collection<P>): Boolean

    fun removePlaylist(playlistId: Int): Boolean = removePlaylists(listOf(playlistId))

    fun removePlaylist(playlist: P): Boolean = removePlaylists(listOf(playlist))

    fun removePlaylists(playlists: Collection<P>): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    fun removePlaylists(playlistIds: Collection<Int>): Boolean

    fun clearAudioItems()

    fun clearPlaylists()

    override val playlists: Set<P>
}