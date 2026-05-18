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

package net.transgressoft.commons.util

import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator

// Drains the reference queue and removes any cache entries whose referent has been reclaimed by
// the GC. The map values are SoftReferences identical to the ones polled from the queue, so a
// reference-equality `remove(key, value)` reliably skips entries that have already been replaced
// by a concurrent `of(...)` call.
internal fun <V : Any> expungeStaleEntries(
    map: ConcurrentHashMap<String, SoftReference<V>>,
    queue: ReferenceQueue<V>
) {
    while (true) {
        val stale = queue.poll() ?: return

        @Suppress("UNCHECKED_CAST")
        val staleRef = stale as SoftReference<V>
        // ConcurrentHashMap does not expose entry iteration that returns the actual SoftReference
        // values without copying, so we iterate. Stale references are rare enough that this is
        // acceptable; under steady-state load the queue is empty and this loop exits immediately.
        for ((key, ref) in map) {
            if (ref === staleRef) {
                map.remove(key, ref)
                break
            }
        }
    }
}