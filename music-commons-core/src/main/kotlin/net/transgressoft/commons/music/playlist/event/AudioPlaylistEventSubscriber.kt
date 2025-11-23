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

package net.transgressoft.commons.music.playlist.event

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.playlist.ReactiveAudioPlaylist

/**
 * Event subscriber for reacting to playlist CRUD operations.
 *
 * Enables components to respond to changes in playlist hierarchies and maintain
 * consistency with playlist modifications across the application.
 */
open class AudioPlaylistEventSubscriber<P: ReactiveAudioPlaylist<I, P>, I: ReactiveAudioItem<I>>(
    name: String
): TransEventSubscriberBase<P, CrudEvent.Type, CrudEvent<Int, P>>(name) {

    override fun toString() =
        buildString {
            append("AudioPlaylistEventSubscriber(name=$name")
            subscription?.let {
                append(", source=${it.source}")
            }
            append(")")
        }
}