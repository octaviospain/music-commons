package net.transgressoft.commons.music.audio.event

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.music.audio.ReactiveAudioItem

open class AudioItemEventSubscriber<I : ReactiveAudioItem<I>>(override val name: String) : TransEventSubscriberBase<I, DataEvent<Int, out I>>() {

    override fun toString() = buildString {
        append("AudioItemEventSubscriber(name=$name")
        subscription?.let {
            append(", source=${it.source}")
        }
        append(")")
    }
}