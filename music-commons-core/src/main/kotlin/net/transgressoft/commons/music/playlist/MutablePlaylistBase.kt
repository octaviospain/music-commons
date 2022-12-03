package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

abstract class MutablePlaylistBase<I: AudioItem>(
    override val id: Int,
    override var isDirectory: Boolean,
    override var name: String,
    _audioItems: List<I> = listOf(),
    _playlists: Set<AudioPlaylist<I>> = setOf()
):ImmutablePlaylistBase<I>(id, isDirectory, name), MutableAudioPlaylist<I> {

    override val audioItems: MutableList<I> = _audioItems.toMutableList()
    override val playlists: MutableSet<AudioPlaylist<I>> = _playlists.toMutableSet()
}