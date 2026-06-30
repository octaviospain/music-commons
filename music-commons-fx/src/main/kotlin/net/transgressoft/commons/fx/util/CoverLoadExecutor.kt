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

package net.transgressoft.commons.fx.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared daemon thread pool that resolves cover art off the JavaFX Application Thread.
 *
 * Observing a cover property (e.g. an audio item's or album's cover) triggers a lazy load. The disk
 * read and image decode run here so the observing thread — typically the JavaFX Application Thread,
 * when a UI cell binds the property — never blocks; the decoded image is published back onto the
 * JavaFX thread by the owner. Threads are daemons so the pool never keeps the JVM alive.
 */
internal object CoverLoadExecutor {

    private val executor: ExecutorService =
        Executors.newFixedThreadPool(maxOf(2, Runtime.getRuntime().availableProcessors() / 2)) { runnable ->
            Thread(runnable, "cover-load").apply { isDaemon = true }
        }

    private val submittedTasks = AtomicInteger(0)

    // Lets tests assert the one-shot load contract at the dispatch level: a working observation
    // latch submits the load exactly once no matter how often the property is observed, whereas a
    // racy latch would submit duplicates that the synchronized resolver would still collapse to the
    // same bytes — invisible to a final-state assertion but visible here.
    internal val submittedTaskCount: Int
        get() = submittedTasks.get()

    internal fun resetSubmittedTaskCount() {
        submittedTasks.set(0)
    }

    fun execute(task: () -> Unit) {
        submittedTasks.incrementAndGet()
        executor.execute(task)
    }
}