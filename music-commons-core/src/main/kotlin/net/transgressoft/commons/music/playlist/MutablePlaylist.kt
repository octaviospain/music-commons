package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

internal class MutablePlaylist<I : AudioItem>(
    override val id: Int,
    override var isDirectory: Boolean,
    override var name: String,
    override val audioItems: MutableList<I> = mutableListOf(),
    override val playlists: MutableSet<AudioPlaylist<I>> = mutableSetOf()
) : ImmutablePlaylist<I>(id, isDirectory, name, audioItems, playlists), MutableAudioPlaylist<I> {

    override fun toAudioPlaylist(): AudioPlaylist<I> = ImmutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet())
}