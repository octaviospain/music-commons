package net.transgressoft.commons.music.playlist.event

import net.transgressoft.commons.TransEventSubscriberBase
import net.transgressoft.commons.data.CrudEvent
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.playlist.ReactiveAudioPlaylist

open class AudioPlaylistEventSubscriber<P: ReactiveAudioPlaylist<I, P>, I: ReactiveAudioItem<I>>(
    name: String
): TransEventSubscriberBase<P, CrudEvent<Int, out P>>(name) {

    override fun toString() =
        buildString {
            append("AudioPlaylistEventSubscriber(name=$name")
            subscription?.let {
                append(", soruce=${it.source}")
            }
            append(")")
    }
}