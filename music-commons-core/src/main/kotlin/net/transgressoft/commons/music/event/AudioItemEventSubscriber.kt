package net.transgressoft.commons.music.event

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import mu.KotlinLogging
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.event.TransEventSubscription
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemEvent

@Serializable
open class AudioItemEventSubscriber<I : AudioItem>(private val name: String) : TransEventSubscriberBase<I, AudioItemEvent>() {

    @Transient private val logger = KotlinLogging.logger {}

    @Transient protected var audioItemSubscription: TransEventSubscription<I>? = null

    init {
        addOnSubscribeEventAction {
            audioItemSubscription = it
            logger.info { "${toString()} subscribed to ${it.source}" }
        }
    }

    override fun toString() = "AudioItemEventSubscriber(name=$name)"
}