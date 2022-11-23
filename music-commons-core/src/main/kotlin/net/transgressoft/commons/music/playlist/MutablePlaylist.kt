package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

internal class MutablePlaylist<I : AudioItem>(
    override val id: Int,
    override var isDirectory: Boolean,
    override var name: String,
    _audioItems: List<I> = listOf(),
    _playlists: Set<AudioPlaylist<I>> = setOf()
) : ImmutablePlaylist<I>(id, isDirectory, name, _audioItems, _playlists), MutableAudioPlaylist<I> {

    override val audioItems: MutableList<I> = _audioItems.toMutableList()
    override val playlists: MutableSet<AudioPlaylist<I>> = _playlists.toMutableSet()

    override fun toAudioPlaylist(): AudioPlaylist<I> = ImmutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet())

    override fun toString(): String {
        return "MutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
    }
}