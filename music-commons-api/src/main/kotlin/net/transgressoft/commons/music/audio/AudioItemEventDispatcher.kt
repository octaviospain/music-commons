package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.EntityEvent
import net.transgressoft.commons.event.EventType
import net.transgressoft.commons.query.QueryEntity
import java.util.concurrent.Flow.Publisher

sealed class AudioItemEventType {

    enum class Type(override val code: Int) : EventType {
        PLAYED(301) {
            override fun <E : QueryEntity> of(entities: Collection<E>): EntityEvent<out E> = Played(entities =  entities)
        }
    }

    private data class Played<E : QueryEntity>(override val type: EventType = Type.PLAYED, override val entities: Collection<E>) : EntityEvent<E>
}

fun <I : AudioItem> EntityEvent<I>.isPlayed(): Boolean = this.type == AudioItemEventType.Type.PLAYED

interface AudioItemEventDispatcher<I : AudioItem> : Publisher<EntityEvent<out I>> {
    fun putPlayedEvent(entities: Collection<I>)
}
