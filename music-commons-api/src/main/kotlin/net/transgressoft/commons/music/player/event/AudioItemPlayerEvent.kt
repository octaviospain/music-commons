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

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
import net.transgressoft.lirp.event.EventType
import net.transgressoft.lirp.event.LirpEvent

/**
 * Sealed class hierarchy representing events emitted by an audio item player.
 * @since 1.0
 */
public sealed class AudioItemPlayerEvent : LirpEvent<AudioItemPlayerEvent.Type> {

    /**
     * The audio item that triggered this event.
     * @since 1.0
     */
    public abstract val audioItem: ReactiveAudioItem<*>

    /**
     * Types of audio player events.
     * @since 1.0
     */
    public enum class Type(
        override val code: Int
    ): EventType {

        PLAYED(210) ;

        override fun toString(): String = "AudioItemPlayerEvent($name, $code)"
    }

    /**
     * Event emitted when an audio item has been played.
     * @since 1.0
     */
    public data class Played(
        override val audioItem: ReactiveAudioItem<*>
    ): AudioItemPlayerEvent() {
        override val type: Type = PLAYED
    }
}