package net.transgressoft.commons.music.event

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import mu.KotlinLogging
import net.transgressoft.commons.event.QueryEntitySubscriberBase
import net.transgressoft.commons.event.QueryEntitySubscriptionBase
import net.transgressoft.commons.music.audio.AudioItem

@Serializable
open class AudioItemEventSubscriber<I : AudioItem>(private val name: String) : QueryEntitySubscriberBase<I>() {

    @Transient private val logger = KotlinLogging.logger {}

    @Transient protected var audioItemSubscription: QueryEntitySubscriptionBase<I>? = null

    init {
        addOnSubscribeEventAction {
            audioItemSubscription = it
            logger.info { "${toString()} subscribed to ${it.source}" }
        }
    }

    override fun toString() = "AudioItemEventSubscriber(name=$name)"
}