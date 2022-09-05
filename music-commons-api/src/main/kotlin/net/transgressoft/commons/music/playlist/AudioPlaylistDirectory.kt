package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

interface AudioPlaylistDirectory<I : AudioItem> : AudioPlaylist<I> {

    fun <N : AudioPlaylist<I>> descendantPlaylists(): Set<N>

    fun <N : AudioPlaylist<I>> containsPlaylist(playlist: N): Boolean
}