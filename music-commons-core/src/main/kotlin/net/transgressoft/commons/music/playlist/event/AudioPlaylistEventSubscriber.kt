package net.transgressoft.commons.music.playlist.event

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.playlist.ReactiveAudioPlaylist

open class AudioPlaylistEventSubscriber<P : ReactiveAudioPlaylist<I, P>, I : ReactiveAudioItem<I>>(override val name: String) : TransEventSubscriberBase<P, DataEvent<Int, out P>>() {

    override fun toString() = buildString {
        append("AudioPlaylistEventSubscriber(name=$name")
        subscription?.let {
            append(", soruce=${it.source}")
        }
        append(")")
    }
}