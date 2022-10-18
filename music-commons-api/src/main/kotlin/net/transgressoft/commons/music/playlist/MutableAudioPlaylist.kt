package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

interface MutableAudioPlaylist<I : AudioItem> : AudioPlaylist<I> {

    override var isDirectory: Boolean

    override var name: String

    override val audioItems: MutableList<I>

    override val playlists: MutableSet<AudioPlaylist<I>>

    fun toAudioPlaylist(): AudioPlaylist<I>
}