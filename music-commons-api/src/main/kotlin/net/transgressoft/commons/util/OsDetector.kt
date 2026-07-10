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

/**
 * Provides a testable seam for OS detection, enabling Windows-specific validation
 * paths to be exercised on Linux CI by overriding [isWindows] via [withOverriddenIsWindows].
 *
 * Tests using the override helper must run sequentially (not in parallel Kotest contexts)
 * because the override is stored in a single volatile field.
 * @since 1.0
 */
public object OsDetector {

    // Process-wide (not thread-local) so an override set on a test thread is visible to library code
    // reading isWindows on another thread — e.g. the JavaFX Application Thread in FX tests. Kept private
    // (non-inline helper below) so it stays off the published binary API.
    @Volatile
    private var overrideIsWindows: Boolean? = null

    public val isWindows: Boolean
        get() = overrideIsWindows ?: System.getProperty("os.name").lowercase().contains("windows")

    /**
     * Executes [block] with [isWindows] temporarily overridden to [value], returning whatever
     * [block] returns. Restores the previous override (or `null` for native detection) in a
     * `finally` block so nested calls do not silently lose the outer override.
     * @since 1.0
     */
    public fun <R> withOverriddenIsWindows(value: Boolean, block: () -> R): R {
        val previous = overrideIsWindows
        overrideIsWindows = value
        return try {
            block()
        } finally {
            overrideIsWindows = previous
        }
    }
}