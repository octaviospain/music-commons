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

package net.transgressoft.commons.music.player

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.util.Duration
import java.util.concurrent.Flow

/**
 * Interface for playing audio items with playback controls and status monitoring.
 *
 * This interface publishes [AudioItemPlayerEvent] to notify subscribers of player state changes.
 */
interface AudioItemPlayer : Flow.Publisher<AudioItemPlayerEvent> {
    /**
     * Represents the possible states of the audio player.
     */
    enum class Status {
        UNKNOWN,
        READY,
        PAUSED,
        PLAYING,
        STOPPED,
        STALLED,
        HALTED,
        DISPOSED
    }

    val totalDuration: Duration
    val volumeProperty: DoubleProperty
    val statusProperty: ReadOnlyObjectProperty<Status>
    val currentTimeProperty: ReadOnlyObjectProperty<Duration>

    fun play(audioItem: ReactiveAudioItem<*>)

    fun pause()

    fun resume()

    fun stop()

    fun dispose()

    fun status(): Status

    fun setVolume(value: Double)

    fun seek(milliSeconds: Double)

    fun onFinish(value: Runnable)
}