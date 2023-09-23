package net.transgressoft.commons.music.audio.event

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.event.TransEventSubscription
import net.transgressoft.commons.music.audio.AudioItem
import mu.KotlinLogging

open class AudioItemEventSubscriber<I : AudioItem>(override val name: String) : TransEventSubscriberBase<I, DataEvent<Int, out I>>() {

    private val logger = KotlinLogging.logger {}

    protected var audioItemSubscription: TransEventSubscription<I>? = null

    init {
        addOnSubscribeEventAction {
            audioItemSubscription = it
            logger.info { "$name subscribed to ${it.source}" }
        }
    }

    override fun toString() = buildString {
        append("AudioItemEventSubscriber(name=$name")
        audioItemSubscription?.let {
            append(", source=${it.source}")
        }
        append(")")
    }
}