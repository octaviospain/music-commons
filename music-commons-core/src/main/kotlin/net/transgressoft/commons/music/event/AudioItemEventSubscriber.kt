package net.transgressoft.commons.music.event

import mu.KotlinLogging
import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.event.TransEventSubscription
import net.transgressoft.commons.music.audio.AudioItem

open class AudioItemEventSubscriber<I : AudioItem>(private val name: String) : TransEventSubscriberBase<I, DataEvent<out I>>() {

    private val logger = KotlinLogging.logger {}

    protected var audioItemSubscription: TransEventSubscription<I>? = null

    init {
        addOnSubscribeEventAction {
            audioItemSubscription = it
            logger.info { "${toString()} subscribed to ${it.source}" }
        }
    }

    override fun toString() = "AudioItemEventSubscriber(name=$name)"
}