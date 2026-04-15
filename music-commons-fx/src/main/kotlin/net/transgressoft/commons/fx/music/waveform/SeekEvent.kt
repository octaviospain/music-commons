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

package net.transgressoft.commons.fx.music.waveform

import javafx.event.Event
import javafx.event.EventTarget
import javafx.event.EventType
import java.io.Serial

/**
 * JavaFX event fired by [PlayableWaveformPane] when the user commits a seek gesture
 * (mouse release after press or drag). Routes through the standard JavaFX event
 * dispatch chain; consumers attach handlers via
 * `addEventHandler(SeekEvent.SEEK) { e -> player.seek(e.seekRatio * totalDurationMs) }`.
 *
 * [seekRatio] is always clamped to [0.0, 1.0] before the event is constructed.
 * Consumers must multiply by the total duration in the units their player expects
 * (e.g., milliseconds for [net.transgressoft.commons.fx.music.player.JavaFxPlayer.seek]).
 */
class SeekEvent(
    source: Any,
    target: EventTarget,
    seekRatio: Double
) : Event(source, target, SEEK) {

    val seekRatio: Double =
        seekRatio
            .takeIf { it.isFinite() }
            ?.coerceIn(0.0, 1.0)
            ?: throw IllegalArgumentException("seekRatio must be finite, got $seekRatio")

    companion object {

        @Serial
        private const val serialVersionUID = 1L

        /**
         * The event type for seek gestures. Namespaced to avoid collisions with
         * other libraries that may register an event type named "SEEK".
         */
        val SEEK: EventType<SeekEvent> =
            EventType(Event.ANY, "net.transgressoft.commons.fx.music.waveform.SEEK")
    }
}