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

package net.transgressoft.commons.music.m3u

import mu.KotlinLogging
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * Line-oriented parser for M3U and M3U8 playlist files.
 *
 * Tolerant of real-world edge cases including BOM markers, CRLF line endings,
 * missing `#EXTM3U` headers, varied directives, and paths containing spaces.
 * Nested playlist references (`.m3u` / `.m3u8`) are surfaced separately so the
 * import service can recurse into them. Directive metadata is deliberately
 * discarded because audio file tags are the source of truth for imported items.
 *
 * Encoding is selected by file extension: `.m3u8` is decoded as strict UTF-8;
 * `.m3u` is decoded as UTF-8 with a Windows-1252 fallback, matching how legacy
 * desktop players (Winamp, foobar2000) typically write the format.
 *
 * @param baseDir directory used to resolve relative paths found in the playlist
 */
internal class M3uParser(private val baseDir: Path) {

    /**
     * Result of parsing an M3U playlist file.
     *
     * @property entries resolved audio file paths in source order
     * @property nestedPlaylists resolved nested M3U/M3U8 references in source order
     */
    data class Result(
        val entries: List<M3uEntry>,
        val nestedPlaylists: List<Path>
    )

    private val logger = KotlinLogging.logger {}

    /**
     * Parses the M3U playlist at the given path.
     *
     * @param m3uPath path to the M3U or M3U8 file
     * @return a [Result] containing entries and nested playlist references
     * @throws M3uParseException if the file is missing, not a regular file, unreadable, or cannot be decoded
     */
    fun parse(m3uPath: Path): Result {
        val content = readContent(m3uPath)
        return parseLines(content.lineSequence())
    }

    private fun readContent(m3uPath: Path): String {
        val bytes =
            try {
                Files.readAllBytes(m3uPath)
            } catch (e: NoSuchFileException) {
                throw M3uParseException("M3U file '${m3uPath.toAbsolutePath()}' does not exist", e)
            } catch (e: IOException) {
                throw M3uParseException("Cannot read M3U file '${m3uPath.toAbsolutePath()}'", e)
            }
        return decode(bytes, m3uPath)
    }

    private fun decode(bytes: ByteArray, m3uPath: Path): String {
        val fileName = m3uPath.fileName?.toString().orEmpty()
        val isM3u8 = fileName.endsWith(".m3u8", ignoreCase = true)
        return if (isM3u8) {
            decodeStrict(bytes, StandardCharsets.UTF_8, m3uPath)
        } else {
            decodeWithFallback(bytes, m3uPath)
        }
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset, m3uPath: Path): String =
        try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw M3uParseException(
                "M3U file '${m3uPath.toAbsolutePath()}' is not valid ${charset.name()}",
                e
            )
        }

    private fun decodeWithFallback(bytes: ByteArray, m3uPath: Path): String =
        try {
            decodeStrict(bytes, StandardCharsets.UTF_8, m3uPath)
        } catch (utf8: M3uParseException) {
            logger.debug { "Falling back to Windows-1252 for ${m3uPath.toAbsolutePath()}: ${utf8.message}" }
            // Windows-1252 is a single-byte encoding with no invalid bytes — decode will always succeed.
            Charset.forName("windows-1252").decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }

    private fun parseLines(lines: Sequence<String>): Result {
        val entries = mutableListOf<M3uEntry>()
        val nestedPlaylists = mutableListOf<Path>()
        for (rawLine in lines) {
            classifyLine(rawLine, entries, nestedPlaylists)
        }
        return Result(entries.toList(), nestedPlaylists.toList())
    }

    private fun classifyLine(
        rawLine: String,
        entries: MutableList<M3uEntry>,
        nestedPlaylists: MutableList<Path>
    ) {
        val line = rawLine.removePrefix(BOM).trim()
        if (line.isEmpty() || line.startsWith("#")) {
            return
        }
        val resolved = resolvePath(line) ?: return
        if (isPlaylistReference(resolved)) {
            nestedPlaylists.add(resolved)
        } else {
            entries.add(M3uEntry(resolved))
        }
    }

    private fun isPlaylistReference(path: Path): Boolean {
        val name = path.fileName?.toString().orEmpty()
        return name.endsWith(".m3u", ignoreCase = true) || name.endsWith(".m3u8", ignoreCase = true)
    }

    private fun resolvePath(line: String): Path? {
        val trimmed = line.trimStart()
        if (isRemoteUri(trimmed)) {
            logger.warn { "Skipping URL path (not supported): $trimmed" }
            return null
        }
        // Use the baseDir's filesystem so absolute path semantics follow the playlist's filesystem,
        // not the JVM default (matters for tests against Jimfs and for cross-platform path handling).
        val candidate = baseDir.fileSystem.getPath(trimmed)
        return if (candidate.isAbsolute) {
            candidate.normalize()
        } else {
            baseDir.resolve(trimmed).normalize()
        }
    }

    private fun isRemoteUri(line: String): Boolean =
        line.startsWith("http://", ignoreCase = true) ||
            line.startsWith("https://", ignoreCase = true) ||
            line.startsWith("file://", ignoreCase = true)

    private fun String.lineSequence(): Sequence<String> = splitToSequence('\n').map { it.removeSuffix("\r") }

    companion object {
        private const val BOM = "\uFEFF"
    }
}