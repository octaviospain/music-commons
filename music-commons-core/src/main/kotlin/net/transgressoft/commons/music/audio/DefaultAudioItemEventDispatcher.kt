package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.DefaultQueryEventDispatcher
import net.transgressoft.commons.event.EventType
import net.transgressoft.commons.event.QueryEntityEvent.Type.*
import net.transgressoft.commons.music.audio.AudioItemEventType.Type.PLAYED
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultAudioItemEventDispatcher(
    executor: ExecutorService = Executors.newCachedThreadPool(),
    activatedEvents: Array<EventType> = arrayOf(CREATE, READ, UPDATE, DELETE, PLAYED),
) : DefaultQueryEventDispatcher<AudioItem>(executor, activatedEvents), AudioItemEventDispatcher<AudioItem> {

    override fun putPlayedEvent(entities: Collection<AudioItem>) {
        if (activatedEvents.contains(PLAYED)) {
            executor.execute { subscribers.forEach { it.onNext(PLAYED.new(entities)) } }
        }
    }
}