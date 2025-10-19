/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.music.player.event

import net.transgressoft.commons.event.EventType
import net.transgressoft.commons.event.TransEvent
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED

/**
 * Sealed class hierarchy representing events emitted by an audio item player.
 */
sealed class AudioItemPlayerEvent : TransEvent<AudioItemPlayerEvent.Type> {

    abstract override val entities: Map<*, ReactiveAudioItem<*>>

    /**
     * Types of audio player events.
     */
    enum class Type(
        override val code: Int
    ): EventType {

        PLAYED(210) ;

        override fun toString() = "AudioItemPlayerEvent($name, $code)"
    }

    /**
     * Event emitted when an audio item has been played.
     */
    data class Played(
        val audioItem: ReactiveAudioItem<*>
    ): AudioItemPlayerEvent() {
        override val type: Type = PLAYED
        override val entities: Map<*, ReactiveAudioItem<*>> = mapOf(audioItem.id to audioItem)
    }
}