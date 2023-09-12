package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.EventType

sealed class StandardAudioItemEvent : AudioItemEvent<ImmutableAudioItem> {

    enum class Type(override val code: Int) : EventType {
        PLAYED(301);
    }

    internal data class Played(override val entities: Collection<ImmutableAudioItem>) : AudioItemEvent<ImmutableAudioItem> {
        override val type: EventType = Type.PLAYED
    }
}

fun StandardAudioItemEvent.isPlayed(): Boolean = this.type == StandardAudioItemEvent.Type.PLAYED
fun StandardAudioItemEvent.Type.of(entities: Collection<ImmutableAudioItem>): AudioItemEvent<ImmutableAudioItem> = StandardAudioItemEvent.Played(entities)