package net.transgressoft.commons.music.event

import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent

class PlayedEventSubscriber(override val name: String = "PlayedEventSubscriber") : TransEventSubscriberBase<ReactiveAudioItem<*>, AudioItemPlayerEvent>() {

    override fun toString() = buildString {
        append("PlayedEventSubscriber(name=$name")
        subscription?.let {
            append(", soruce=${it.source}")
        }
        append(")")
    }
}
