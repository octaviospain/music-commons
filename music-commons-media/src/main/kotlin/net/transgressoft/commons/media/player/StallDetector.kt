/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.media.player

import net.transgressoft.commons.music.player.AudioItemPlayer.Status

/**
 * Detects and recovers from PCM read stalls in the pump loop.
 *
 * A stall is declared when a single [update] call reports a read duration that meets or
 * exceeds [stallThresholdNanos]. Recovery restores the player status via [setStatus] when a
 * subsequent read completes within the threshold. State is accessed exclusively through the
 * supplied lambdas so that this class holds no back-reference to the player.
 *
 * @param stallThresholdNanos nanosecond duration above which a single read is considered stalled
 * @param isPlaying returns true while the player is actively playing (not paused or stopped)
 * @param isPaused returns true while the player is paused
 * @param getStatus returns the current player [Status]
 * @param setStatus applies a new [Status] to the player
 */
internal class StallDetector(
    private val stallThresholdNanos: Long,
    private val isPlaying: () -> Boolean,
    private val isPaused: () -> Boolean,
    private val getStatus: () -> Status,
    private val setStatus: (Status) -> Unit
) {

    /**
     * Evaluates a PCM read duration and either declares a stall or recovers from one.
     *
     * Sets the status to [Status.STALLED] when [readDurationNanos] meets or exceeds the
     * threshold and the player is playing. Otherwise delegates to [recoverIfNeeded].
     *
     * @param readDurationNanos elapsed nanoseconds for the most recent PCM read call
     */
    fun update(readDurationNanos: Long) {
        if (readDurationNanos >= stallThresholdNanos && isPlaying()) {
            setStatus(Status.STALLED)
        } else {
            recoverIfNeeded()
        }
    }

    /**
     * Restores the player status from [Status.STALLED] to either [Status.PAUSED] or
     * [Status.PLAYING] depending on the current player state. Has no effect when the
     * status is not [Status.STALLED].
     */
    fun recoverIfNeeded() {
        if (getStatus() != Status.STALLED) return
        setStatus(if (isPaused()) Status.PAUSED else Status.PLAYING)
    }
}