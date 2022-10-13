package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.EntityEvent
import net.transgressoft.commons.event.EventEntity
import net.transgressoft.commons.event.EventType

sealed class AudioItemEventType {

    enum class Type(override val code: Int) : EventType {
        PLAYED(301) {
            override fun <E : EventEntity> of(entities: Collection<E>): EntityEvent<out E> = Played(entities =  entities)
        },
        ADDED_ARTIST(302) {
            override fun <E : EventEntity> of(entities: Collection<E>): EntityEvent<out E> = AddedArtist(entities = entities)
        },
        REMOVED_ARTIST(303) {
            override fun <E : EventEntity> of(entities: Collection<E>): EntityEvent<out E> = RemovedArtist(entities = entities)
        }
    }

    private data class Played<E : EventEntity>(override val type: EventType = Type.PLAYED, override val entities: Collection<E>) : EntityEvent<E>
    private data class AddedArtist<E : EventEntity>(override val type: EventType = Type.ADDED_ARTIST, override val entities: Collection<E>) : EntityEvent<E>
    private data class RemovedArtist<E : EventEntity>(override val type: EventType = Type.REMOVED_ARTIST, override val entities: Collection<E>) : EntityEvent<E>
}

fun <I : AudioItem> EntityEvent<I>.isPlayed(): Boolean = this.type == AudioItemEventType.Type.PLAYED
