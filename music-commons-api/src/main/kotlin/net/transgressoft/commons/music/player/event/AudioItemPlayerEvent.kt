package net.transgressoft.commons.music.player.event

import net.transgressoft.commons.event.EventType
import net.transgressoft.commons.event.TransEvent
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.*

sealed class AudioItemPlayerEvent : TransEvent {

    abstract override val entitiesById: Map<*,  ReactiveAudioItem<*>>

    enum class Type(override val code: Int) : EventType {
        PLAYED(210);

        override fun toString() = "AudioItemPlayerEvent(${name}, ${code})"
    }

    data class Played(val audioItem: ReactiveAudioItem<*>) : AudioItemPlayerEvent() {
        override val type: EventType = PLAYED
        override val entitiesById: Map<*, ReactiveAudioItem<*>> = mapOf(audioItem.id to audioItem)
    }
}
