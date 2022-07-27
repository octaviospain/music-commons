package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.DefaultQueryEventDispatcher
import net.transgressoft.commons.event.EventType
import net.transgressoft.commons.event.QueryEntityEvent.Type.*
import net.transgressoft.commons.music.audio.AudioItemEventType.Type.PLAYED
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultAudioItemEventDispatcher<I : AudioItem>(
    executor: ExecutorService = Executors.newCachedThreadPool(),
    activatedEvents: Array<EventType> = arrayOf(CREATE, READ, UPDATE, DELETE, PLAYED),
) : DefaultQueryEventDispatcher<I>(executor, activatedEvents), AudioItemEventDispatcher<I> {

    override fun putPlayedEvent(entities: Collection<I>) {
        if (activatedEvents.contains(PLAYED)) {
            executor.execute { subscribers.forEach { it.onNext(PLAYED.of(entities)) } }
        }
    }
}