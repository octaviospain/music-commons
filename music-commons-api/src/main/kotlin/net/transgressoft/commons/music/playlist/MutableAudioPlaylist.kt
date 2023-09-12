package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

interface MutableAudioPlaylist<I : AudioItem> : AudioPlaylist<I> {

    override var isDirectory: Boolean

    override val audioItems: MutableList<I>

    override val playlists: MutableSet<MutableAudioPlaylist<I>>

    fun toAudioPlaylist(): AudioPlaylist<I>
}