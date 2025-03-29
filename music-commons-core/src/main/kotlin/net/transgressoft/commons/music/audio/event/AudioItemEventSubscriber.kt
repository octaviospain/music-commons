package net.transgressoft.commons.music.audio.event

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.music.audio.ReactiveAudioItem

open class AudioItemEventSubscriber<I: ReactiveAudioItem<I>>(
    name: String
): TransEventSubscriberBase<I, CrudEvent<Int, out I>>(name) {

    override fun toString() =
        buildString {
            append("AudioItemEventSubscriber(name=$name")
            subscription?.let {
                append(", source=${it.source}")
            }
            append(")")
        }
}