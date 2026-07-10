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

import java.nio.file.Path

/**
 * Validates paths and names against Windows filesystem constraints.
 *
 * Triggers when the host is Windows AND the path uses Windows naming conventions — detected by
 * the [java.nio.file.FileSystem.getSeparator] being `\`. Paths on filesystems with `/` separators
 * (Jimfs unix configuration, ZipFS, custom Unix-style providers) bypass validation even on Windows
 * hosts, because they don't reach the Win32 IO layer and routinely use characters like `:`
 * legitimately (e.g. Jimfs parsing `file:///C:/...` URIs into segments).
 *
 * When active: forbidden characters, reserved names (case-insensitive, with or without
 * extension), trailing dots or spaces, and MAX_PATH (260 chars) are all enforced.
 * [sanitizeForTempFile] is the only method that strips rather than throws, for use with temp file
 * names which are an internal implementation detail.
 * @since 1.0
 */
public object WindowsPathValidator {

    // Per Microsoft naming docs, characters in 0x00-0x1F are also forbidden in path segments.
    private val FORBIDDEN_CHARS =
        setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*') + (0..31).map { it.toChar() }.toSet()

    // [VERIFIED: https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file]
    // COM0 and LPT0 are included — some older sources omit them; Microsoft docs include them.
    // Superscript variants (COM¹/COM²/COM³, LPT¹/LPT²/LPT³) are also reserved per the same docs.
    private val RESERVED_NAMES =
        setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM0", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "COM¹", "COM²", "COM³",
            "LPT0", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
            "LPT¹", "LPT²", "LPT³"
        )

    private const val MAX_PATH = 260

    /**
     * Validates [path] against all Windows filesystem rules. No-op on non-Windows JVMs and on
     * paths whose [java.nio.file.FileSystem] uses a non-`\` separator (i.e. Unix-style paths on
     * Jimfs unix configuration or similar) — those paths never reach the Win32 IO layer, so the
     * Windows naming contract does not apply.
     * @since 1.0
     */
    public fun validatePath(path: Path) {
        if (!OsDetector.isWindows) return
        if (path.fileSystem.separator != "\\") return
        val fullPathString = path.toAbsolutePath().toString()
        // MAX_PATH includes the trailing null terminator, so a 260-char path string already exceeds
        // the practical limit for `CreateFileW` without the `\\?\` prefix. Use >= to match the same
        // boundary that WindowsLongPathSupport uses when deciding to apply the long-path prefix.
        if (fullPathString.length >= MAX_PATH) {
            throw WindowsPathException(fullPathString, WindowsViolation.ExceedsMaxPath)
        }
        path.forEach { segment -> validateName(segment.toString(), fullPathString) }
    }

    /**
     * Validates a single name segment (file or directory name, or a playlist name).
     * No-op on non-Windows JVMs.
     *
     * @param name the name to validate
     * @param fullPath optional full path for error context; defaults to [name] itself
     * @since 1.0
     */
    public fun validateName(name: String, fullPath: String? = null) {
        if (!OsDetector.isWindows) return
        val offending = fullPath ?: name
        name.find { it in FORBIDDEN_CHARS }?.let { ch ->
            throw WindowsPathException(offending, WindowsViolation.ForbiddenChar(ch))
        }
        // Use substringBefore (first dot), not substringBeforeLast: per Microsoft docs, "NUL.tar.gz"
        // is equivalent to "NUL". Splitting on the last dot would extract "NUL.tar" and let the name through.
        val stem = name.substringBefore(".")
        if (stem.uppercase() in RESERVED_NAMES) {
            throw WindowsPathException(offending, WindowsViolation.ReservedName(stem))
        }
        if (name.endsWith('.') || name.endsWith(' ')) {
            throw WindowsPathException(offending, WindowsViolation.TrailingDotOrSpace)
        }
    }

    /**
     * Strips Windows-forbidden characters and trailing dots/spaces from [name] for use as a
     * temp-file prefix. Never throws. Returns "_" if the input sanitizes to empty.
     * @since 1.0
     */
    public fun sanitizeForTempFile(name: String): String =
        name.map { if (it in FORBIDDEN_CHARS) '_' else it }
            .joinToString("")
            .trimEnd('.', ' ')
            .ifEmpty { "_" }
}