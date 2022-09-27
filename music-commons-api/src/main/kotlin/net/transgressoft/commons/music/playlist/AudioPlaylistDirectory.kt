package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

interface AudioPlaylistDirectory<I : AudioItem, N : AudioPlaylist<I>> : AudioPlaylist<I> {

    fun descendantPlaylists(): Set<N>

    fun containsPlaylist(playlist: N): Boolean
}