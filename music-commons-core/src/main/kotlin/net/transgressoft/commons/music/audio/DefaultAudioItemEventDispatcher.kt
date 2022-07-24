package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.AudioItemEventType.Type.PLAYED
import net.transgressoft.commons.query.EventType
import net.transgressoft.commons.query.QueryEntityEvent.Type.*
import net.transgressoft.commons.query.QueryEventDispatcherBase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultAudioItemEventDispatcher(
    executor: ExecutorService = Executors.newCachedThreadPool(),
    activatedEvents: Array<EventType> = arrayOf(CREATE, READ, UPDATE, DELETE, PLAYED),
) : QueryEventDispatcherBase<AudioItem>(executor, activatedEvents), AudioItemEventDispatcher<AudioItem> {

    override fun putPlayedEvent(entities: Collection<AudioItem>) {
        if (activatedEvents.contains(PLAYED)) {
            executor.execute { subscribers.forEach { it.onNext(PLAYED.new(entities)) } }
        }
    }
}
