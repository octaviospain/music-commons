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

import java.io.File
import java.nio.file.Path

/**
 * Transparently applies the extended-length path prefix on Windows when a path reaches
 * the MAX_PATH (260) boundary. Consumers pass paths through this utility before handing
 * them to filesystem APIs (Jave2, `Files.*`); the prefix is never visible to callers or
 * persisted in JSON.
 *
 * Local volume paths are prefixed with `\\?\` (e.g. `\\?\C:\very\long\path`).
 * UNC paths (`\\server\share\...`) are rewritten with the `\\?\UNC\` prefix
 * (e.g. `\\?\UNC\server\share\...`) — using the plain `\\?\` form on a UNC path
 * produces an invalid `\\?\\\server\share\...` that Windows rejects.
 *
 * On non-Windows JVMs all methods return the input unchanged.
 */
object WindowsLongPathSupport {

    // MAX_PATH is 260 *including* the null terminator, so 260-char strings already exceed the limit.
    private const val MAX_PATH = 260
    private const val LONG_PATH_PREFIX = "\\\\?\\"
    private const val UNC_PATH_PREFIX = "\\\\?\\UNC\\"
    private const val UNC_LEADING = "\\\\"

    /** Applies the long-path prefix to [path] if needed on Windows. Returns the input unchanged on non-Windows. */
    fun toLongPathSafe(path: Path): Path {
        if (!OsDetector.isWindows) return path
        val raw = path.toString()
        val absolute = path.toAbsolutePath().toString()
        val prefixed = applyPrefix(raw, absolute) ?: return path
        return Path.of(prefixed)
    }

    /** File-valued overload for call sites using [java.io.File]. Returns the input unchanged on non-Windows. */
    fun toLongPathSafe(file: File): File {
        if (!OsDetector.isWindows) return file
        val raw = file.path
        val absolute = file.absolutePath
        val prefixed = applyPrefix(raw, absolute) ?: return file
        return File(prefixed)
    }

    // The raw input is consulted for prefix/UNC detection in addition to the absolute form so that
    // a non-Windows JVM running with `OsDetector.withOverriddenIsWindows(true)` (test seam) doesn't
    // mis-prefix inputs that the host's `toAbsolutePath()` would otherwise mangle.
    private fun applyPrefix(raw: String, absolute: String): String? {
        if (raw.startsWith(LONG_PATH_PREFIX) || absolute.startsWith(LONG_PATH_PREFIX)) return null
        val isUnc = raw.startsWith(UNC_LEADING) || absolute.startsWith(UNC_LEADING)
        // For UNC inputs, prefer the raw form: host absolutization on non-Windows JVMs may treat
        // `\\server\share\...` as a relative segment and prepend the cwd, breaking detection.
        val effective = if (isUnc && raw.startsWith(UNC_LEADING)) raw else absolute
        if (effective.length < MAX_PATH) return null
        return if (effective.startsWith(UNC_LEADING)) {
            UNC_PATH_PREFIX + effective.removePrefix(UNC_LEADING)
        } else {
            LONG_PATH_PREFIX + effective
        }
    }
}