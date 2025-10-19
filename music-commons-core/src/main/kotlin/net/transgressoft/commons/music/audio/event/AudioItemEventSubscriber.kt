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

package net.transgressoft.commons.music.audio.event

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.music.audio.ReactiveAudioItem

/**
 * Event subscriber for reacting to audio item CRUD operations.
 *
 * Allows components like playlists and waveform repositories to stay synchronized
 * with audio library changes by listening to create, update, and delete events.
 */
open class AudioItemEventSubscriber<I: ReactiveAudioItem<I>>(
    name: String
): TransEventSubscriberBase<I, CrudEvent.Type, CrudEvent<Int, out I>>(name) {

    override fun toString() =
        buildString {
            append("AudioItemEventSubscriber(name=$name")
            subscription?.let {
                append(", source=${it.source}")
            }
            append(")")
        }
}