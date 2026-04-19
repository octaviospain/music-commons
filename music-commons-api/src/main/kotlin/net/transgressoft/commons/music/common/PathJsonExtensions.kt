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

import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.SerializationException

/**
 * Converts this path to a platform-neutral `file://` URI string for JSON persistence.
 *
 * Delegates to [Path.toAbsolutePath] before calling [Path.toUri] to satisfy the
 * JDK contract that requires an absolute path; [Path.toUri] handles OS-specific
 * encoding including drive letters, separator normalization, and percent-encoding
 * of spaces and special characters.
 *
 * Note: paths from non-default filesystem providers (e.g. Jimfs) serialize with
 * their provider's URI scheme (`jimfs://...`) and will not round-trip through
 * [toPathFromJsonUri], which only accepts the `file://` scheme. This is
 * intentional: production code should serialize real filesystem paths only.
 */
fun Path.toJsonUri(): String = toAbsolutePath().toUri().toString()

/**
 * Reconstructs a [Path] from a `file://` URI string produced by [toJsonUri].
 *
 * @throws SerializationException if the string does not start with `file://`
 *         (indicating pre-25.1 JSON using raw OS path strings — no migration
 *         fallback is provided), or if the URI is structurally invalid or
 *         points at a filesystem provider that is not registered in this JVM.
 */
fun String.toPathFromJsonUri(): Path {
    if (!startsWith("file://")) {
        throw SerializationException(
            "Invalid path format: expected a file:// URI but found '$this'. " +
                "This JSON file was written by a pre-25.1 version and is no longer supported."
        )
    }
    return try {
        Paths.get(URI(this))
    } catch (e: Exception) {
        throw SerializationException("Invalid file:// URI: '$this'", e)
    }
}