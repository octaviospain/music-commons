package net.transgressoft.commons.music.player.event

import net.transgressoft.commons.event.EventType
import net.transgressoft.commons.event.TransEvent
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED

sealed class AudioItemPlayerEvent : TransEvent<AudioItemPlayerEvent.Type> {

    abstract override val entities: Map<*, ReactiveAudioItem<*>>

    enum class Type(
        override val code: Int
    ): EventType {

        PLAYED(210) ;

        override fun toString() = "AudioItemPlayerEvent($name, $code)"
    }

    data class Played(
        val audioItem: ReactiveAudioItem<*>
    ): AudioItemPlayerEvent() {
        override val type: Type = PLAYED
        override val entities: Map<*, ReactiveAudioItem<*>> = mapOf(audioItem.id to audioItem)
    }
}