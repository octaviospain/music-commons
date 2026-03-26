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

package net.transgressoft.commons.music.event

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.lirp.event.LirpEventSubscriberBase

/**
 * Event subscriber for tracking audio item playback events.
 *
 * Connects audio libraries to player components, allowing libraries to react to
 * playback events such as updating play counts or triggering related actions.
 */
open class PlayedEventSubscriber: LirpEventSubscriberBase<ReactiveAudioItem<*>, AudioItemPlayerEvent.Type, AudioItemPlayerEvent>("PlayedEventSubscriber") {

    /**
     * Cancels the current subscription, stopping event delivery from the publisher.
     */
    fun cancelSubscription() {
        subscription?.cancel()
    }

    override fun toString() =
        buildString {
            append("PlayedEventSubscriber(name=$name")
            subscription?.let {
                append(", source=${it.source}")
            }
            append(")")
        }
}