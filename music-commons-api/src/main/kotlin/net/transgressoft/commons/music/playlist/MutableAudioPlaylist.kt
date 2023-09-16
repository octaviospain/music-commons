package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

interface MutableAudioPlaylist<I : AudioItem> : AudioPlaylist<I> {

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

    fun addPlaylist(playlist: MutableAudioPlaylist<I>): Boolean = addPlaylists(listOf(playlist))

    fun addPlaylists(playlists: Collection<MutableAudioPlaylist<I>>): Boolean

    fun removePlaylist(playlistId: Int): Boolean = removePlaylists(listOf(playlistId))

    fun removePlaylist(playlist: MutableAudioPlaylist<I>): Boolean = removePlaylists(listOf(playlist))

    fun removePlaylists(playlists: Collection<MutableAudioPlaylist<I>>): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    fun removePlaylists(playlistIds: Collection<Int>): Boolean

    fun clearAudioItems()

    fun clearPlaylists()

    override val playlists: Set<MutableAudioPlaylist<I>>

    fun toImmutableAudioPlaylist(): AudioPlaylist<I>
}