package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem

interface MutableAudioPlaylist<I : AudioItem> : AudioPlaylist<I> {

    override var name: String

    fun addAudioItems(vararg audioItems: I) {
        addAudioItems(listOf(*audioItems))
    }

    fun addAudioItems(audioItems: Collection<I>)

    fun removeAudioItems(vararg audioItems: I) {
        removeAudioItems(setOf(*audioItems))
    }

    fun removeAudioItems(audioItems: Collection<I>)

    fun clearAudioItems()
}