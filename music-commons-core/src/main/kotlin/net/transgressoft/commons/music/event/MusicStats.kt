package net.transgressoft.commons.music.event

import mu.KotlinLogging
import net.transgressoft.commons.event.QueryEntityEvent.Type.READ
import net.transgressoft.commons.event.QueryEntitySubscription
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemEventType.Type.PLAYED

class MusicStats : AudioItemEventSubscriber<AudioItem>() {

    private val log = KotlinLogging.logger {}

    private val playCounts: MutableMap<Int, Short> = HashMap()

    private var audioItemSubscription: QueryEntitySubscription<AudioItem>? = null

    init {
        addOnSubscribeEventAction { subscription ->
            audioItemSubscription = subscription
            log.info("MusicLibrary subscribed to AudioItem events from ${subscription.source}")
        }
        addOnNextEventAction(PLAYED) { event ->
            event.entities.forEach {
                playCounts[it.id] = playCounts.getOrDefault(it.id, 0).plus(1).toShort()
            }
        }
        addOnNextEventAction(READ) { event ->
            log.debug("{} were read", event.entities)
        }
        addOnErrorEventAction { throwable ->
            log.error("Exception while subscribed to AudioItemEvents", throwable)
            audioItemSubscription?.cancel()
            audioItemSubscription = null
        }
        addOnCompleteEventAction {
            audioItemSubscription = null
        }
    }
}