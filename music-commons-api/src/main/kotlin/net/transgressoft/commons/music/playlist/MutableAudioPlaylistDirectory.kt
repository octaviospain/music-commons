package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

interface MutableAudioPlaylistDirectory<I : AudioItem> : AudioPlaylistDirectory<I>, MutableAudioPlaylist<I> {

    fun <N : AudioPlaylist<I>> addPlaylists(vararg playlists: N) {
        addPlaylists(setOf(*playlists))
    }

    fun <N : AudioPlaylist<I>> addPlaylists(playlists: Set<N>)

    fun <N : AudioPlaylist<I>> removePlaylists(vararg playlists: N) {
        removePlaylists(setOf(*playlists))
    }

    fun <N : AudioPlaylist<I>> removePlaylists(playlists: Set<N>)
}