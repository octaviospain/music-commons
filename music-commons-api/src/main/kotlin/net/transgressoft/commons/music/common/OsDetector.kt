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

package net.transgressoft.commons.music.common

/**
 * Provides a testable seam for OS detection, enabling Windows-specific validation
 * paths to be exercised on Linux CI by overriding [isWindows] via [withOverriddenIsWindows].
 *
 * Tests using the override helper must run sequentially (not in parallel Kotest contexts)
 * because the override is stored in a single volatile field.
 */
object OsDetector {

    @Volatile
    @PublishedApi
    internal var overrideIsWindows: Boolean? = null

    val isWindows: Boolean
        get() = overrideIsWindows ?: System.getProperty("os.name").lowercase().contains("windows")

    /**
     * Executes [block] with [isWindows] temporarily overridden to [value], returning whatever
     * [block] returns. Restores the previous override (or `null` for native detection) in a
     * `finally` block so nested calls do not silently lose the outer override.
     */
    inline fun <R> withOverriddenIsWindows(value: Boolean, block: () -> R): R {
        val previous = overrideIsWindows
        overrideIsWindows = value
        return try {
            block()
        } finally {
            overrideIsWindows = previous
        }
    }
}