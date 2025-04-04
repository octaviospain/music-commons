package net.transgressoft.commons.music.event

import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent

class PlayedEventSubscriber: TransEventSubscriberBase<ReactiveAudioItem<*>, AudioItemPlayerEvent.Type, AudioItemPlayerEvent>("PlayedEventSubscriber") {

    override fun toString() =
        buildString {
            append("PlayedEventSubscriber(name=$name")
            subscription?.let {
                append(", source=${it.source}")
            }
            append(")")
        }
}