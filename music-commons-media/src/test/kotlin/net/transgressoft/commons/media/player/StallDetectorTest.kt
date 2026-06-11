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
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicReference

@DisplayName("StallDetector")
internal class StallDetectorTest : StringSpec({

    val threshold = 100_000_000L // 100ms in nanos

    fun detector(
        statusRef: AtomicReference<Status>,
        isPlaying: () -> Boolean,
        isPaused: () -> Boolean
    ) = StallDetector(
        stallThresholdNanos = threshold,
        isPlaying = isPlaying,
        isPaused = isPaused,
        getStatus = { statusRef.get() },
        setStatus = { statusRef.set(it) }
    )

    "StallDetector sets STALLED when read duration meets threshold while playing" {
        val statusRef = AtomicReference(Status.PLAYING)
        val d = detector(statusRef, isPlaying = { true }, isPaused = { false })

        d.update(threshold)

        statusRef.get() shouldBe Status.STALLED
    }

    "StallDetector recovers STALLED to PLAYING when read duration drops below threshold" {
        val statusRef = AtomicReference(Status.STALLED)
        val d = detector(statusRef, isPlaying = { true }, isPaused = { false })

        d.update(threshold - 1)

        statusRef.get() shouldBe Status.PLAYING
    }

    "StallDetector recovers STALLED to PAUSED when player is paused" {
        val statusRef = AtomicReference(Status.STALLED)
        val d = detector(statusRef, isPlaying = { false }, isPaused = { true })

        d.update(threshold - 1)

        statusRef.get() shouldBe Status.PAUSED
    }

    "StallDetector recoverIfNeeded is a no-op when status is not STALLED" {
        val statusRef = AtomicReference(Status.PLAYING)
        val d = detector(statusRef, isPlaying = { true }, isPaused = { false })

        d.recoverIfNeeded()

        statusRef.get() shouldBe Status.PLAYING
    }
})