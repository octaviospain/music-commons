package net.transgressoft.commons.music.event

import mu.KotlinLogging
import net.transgressoft.commons.event.QueryEntitySubscriberBase
import net.transgressoft.commons.event.QueryEntitySubscription
import net.transgressoft.commons.music.audio.AudioItem

open class AudioItemEventSubscriber<I : AudioItem> : QueryEntitySubscriberBase<I>() {

    private val logger = KotlinLogging.logger {}

    protected var audioItemSubscription: QueryEntitySubscription<I>? = null

    init {
        addOnSubscribeEventAction {
            audioItemSubscription = it
            logger.info { "${toString()} subscribed to ${it.source}" }
        }
    }
}