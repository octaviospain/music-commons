package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.event.EventType

sealed class AudioItemEvent : DataEvent<AudioItem> {

    enum class Type(override val code: Int) : EventType {
        PLAYED(301);
    }

    internal data class Played(override val entities: Collection<AudioItem>) : AudioItemEvent() {
        override val type: EventType = Type.PLAYED
    }
}

fun AudioItemEvent.isPlayed(): Boolean = this.type == AudioItemEvent.Type.PLAYED
fun AudioItemEvent.Type.of(entities: Collection<AudioItem>): AudioItemEvent = AudioItemEvent.Played(entities)