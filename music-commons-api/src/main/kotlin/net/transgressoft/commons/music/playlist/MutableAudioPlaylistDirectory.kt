package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

interface MutableAudioPlaylistDirectory<I : AudioItem, N : AudioPlaylist<I>> : AudioPlaylistDirectory<I, N>, MutableAudioPlaylist<I> {

    fun addPlaylists(vararg playlists: N) {
        addPlaylists(setOf(*playlists))
    }

    fun addPlaylists(playlists: Set<N>)

    fun removePlaylists(vararg playlists: N) {
        removePlaylists(setOf(*playlists))
    }

    fun removePlaylists(playlists: Set<N>)
}