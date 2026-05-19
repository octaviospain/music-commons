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

import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.SerializationException

/**
 * Converts this path to a platform-neutral `file://` URI string for JSON persistence.
 *
 * Default-filesystem paths use [toUri] which handles OS-specific encoding (drive letters,
 * separator normalization, percent-encoding). Paths from non-default filesystem providers
 * (e.g. Jimfs) would otherwise serialize with their provider's URI scheme (`jimfs://...`),
 * which would fail the `file://` guard in [toPathFromJsonUri] on round-trip. To keep the
 * serialized form stable across filesystems, non-default-FS paths emit a synthetic `file://`
 * URI built from the absolute path string.
 */
fun Path.toJsonUri(): String =
    if (fileSystem == FileSystems.getDefault()) {
        toAbsolutePath().toUri().toString()
    } else {
        // Synthesize a file:// URI from the path string so toPathFromJsonUri's `file://` guard
        // passes. Use URI's 4-arg constructor to percent-encode spaces, backslashes, drive
        // letters, and other reserved characters that would otherwise break URI(this) round-trip
        // in toPathFromJsonUri. Ensure the path starts with '/' so the resulting URI has the
        // canonical file:///... shape (Jimfs windows paths like `C:\a\b` would otherwise produce
        // a relative-form URI).
        val absolute = toAbsolutePath().toString()
        val pathForUri = if (absolute.startsWith("/")) absolute else "/$absolute"
        // 5-arg URI(scheme, authority, path, query, fragment) with empty-string authority emits
        // the canonical file:///... triple-slash form (file://<authority>/<path>).
        URI("file", "", pathForUri, null, null).toString()
    }

/**
 * Reconstructs a [Path] from a `file://` URI string produced by [toJsonUri], using the
 * default filesystem.
 *
 * @throws SerializationException if the string does not start with `file://`
 *         (indicating pre-25.1 JSON using raw OS path strings — no migration
 *         fallback is provided), or if the URI is structurally invalid or
 *         points at a filesystem provider that is not registered in this JVM.
 */
fun String.toPathFromJsonUri(): Path = toPathFromJsonUri(FileSystems.getDefault())

/**
 * Reconstructs a [Path] from a `file://` URI string against an explicit [FileSystem].
 *
 * Strips the URI scheme/authority and resolves the path component against
 * [fileSystem]. This allows JSON persisted from one filesystem (e.g. real disk)
 * to be deserialized against another (e.g. Jimfs) by passing the target filesystem
 * explicitly.
 *
 * @param fileSystem the [FileSystem] used to materialize the resulting [Path].
 * @throws SerializationException if the string does not start with `file://`,
 *         or if the URI is structurally invalid.
 */
fun String.toPathFromJsonUri(fileSystem: FileSystem): Path {
    if (!startsWith("file://")) {
        throw SerializationException(
            "Invalid path format: expected a file:// URI but found '$this'. " +
                "This JSON file was written by a pre-25.1 version and is no longer supported."
        )
    }
    return try {
        val uri = URI(this)
        if (fileSystem == FileSystems.getDefault()) {
            // Paths.get(URI) is the URI-aware path on the default provider: it handles
            // Windows drive letters (file:///C:/... -> C:\...), UNC authority preservation
            // (file:////server/share/... -> \\server\share\...), and POSIX paths
            // (file:///home/... -> /home/...) without manual string surgery.
            Paths.get(uri)
        } else {
            // Non-default filesystems (e.g. Jimfs) need explicit FileSystem.getPath. Strip the
            // leading slash for Windows-style absolute paths like `/C:\foo\bar` so the
            // Jimfs windows-config parses the drive-rooted form.
            val rawPath = uri.path
            val normalized =
                if (rawPath.length > 3 && rawPath[0] == '/' && rawPath[2] == ':') {
                    rawPath.substring(1)
                } else {
                    rawPath
                }
            fileSystem.getPath(normalized)
        }
    } catch (e: Exception) {
        throw SerializationException("Invalid file:// URI: '$this'", e)
    }
}