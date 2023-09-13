package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

abstract class MutablePlaylistBase<I: AudioItem>(
    override var id: Int,
    override var isDirectory: Boolean,
    override var name: String,
    audioItems: List<I> = listOf(),
    playlists: Set<MutableAudioPlaylist<I>> = setOf()
): ImmutablePlaylistBase<I>(name), MutableAudioPlaylist<I> {

    override val audioItems: MutableList<I> = ArrayList(audioItems)
    override val playlists: MutableSet<MutableAudioPlaylist<I>> = HashSet(playlists)
}