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

package net.transgressoft.commons.fx.music

import io.kotest.assertions.nondeterministic.eventually
import org.testfx.util.WaitForAsyncUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Retries [assertion] until it passes or [duration] elapses, draining the JavaFX event queue on
 * each attempt.
 *
 * The `waitForFxEvents()` call must live inside the retry window: FX projection updates are posted
 * to the application thread asynchronously, so pumping the queue once before the loop races the
 * projection on slower CI runners. Draining on every retry lets each attempt observe the latest
 * settled state.
 *
 * @param duration the maximum time to keep retrying before failing
 * @param assertion the assertion block re-evaluated after each FX-queue drain
 */
suspend fun eventuallyAfterFxEvents(
    duration: Duration = 2.seconds,
    assertion: () -> Unit
) {
    eventually(duration) {
        WaitForAsyncUtils.waitForFxEvents()
        assertion()
    }
}

/**
 * Drains the JavaFX event queue when a toolkit is running, swallowing the failure when it is not.
 *
 * In a headless context where the JavaFX toolkit has not been started, `waitForFxEvents()` throws;
 * the inline fallback in the code under test has already applied the update synchronously, so the
 * failure is expected and ignored. When a toolkit is running (e.g. an FX-enabled test container),
 * this drains the queue so the caller observes the settled state.
 */
fun drainFxEventsIfToolkitRunning() {
    try {
        WaitForAsyncUtils.waitForFxEvents()
    } catch (_: Exception) {
        // Toolkit not initialized — the inline fallback already applied the update.
    }
}