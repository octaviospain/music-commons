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

import net.transgressoft.commons.music.audio.AudioFileTagType
import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.commons.music.audio.AudioItemTestAttributes
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.VirtualFiles
import net.transgressoft.commons.music.audio.audioAttributes
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.property.arbitrary.next
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads static M3U playlist fixtures from the classpath and materializes virtual
 * audio files at the paths each playlist references.
 *
 * The static M3U files under `src/main/resources/m3u/` mirror the real-world
 * layout `../Library/<artist>/<album>/NN Title.ext` (or
 * `../Library/Compilations/<album>/NN Title.ext`), so fixtures derive realistic
 * artist, album, track number and title values from each entry path. EXTART,
 * EXTALB and EXTINF directives, when present, override the path-derived values.
 *
 * The .m3u file (and any nested `.m3u` references) are copied verbatim into the
 * supplied [FileSystem] under [playlistsDir], so the parser sees the exact bytes
 * shipped in the resource — including BOMs, CRLFs and non-UTF-8 encodings.
 */
object M3uTestFixtures {

    private const val DEFAULT_PLAYLISTS_DIR = "/music/Playlists"
    private const val COMPILATIONS_LABEL = "Compilations"
    private const val COMPILATION_ARTIST = "Various Artists"

    /**
     * Loads the M3U fixture at the given classpath [resourceName] and prepares a
     * [PlaylistFixture] backed by [fileSystem]. Nested M3U references are
     * recursively copied to the same filesystem.
     */
    fun loadPlaylist(
        resourceName: String,
        fileSystem: FileSystem = Jimfs.newFileSystem(Configuration.unix()),
        playlistsDir: String = DEFAULT_PLAYLISTS_DIR
    ): PlaylistFixture {
        val baseDir = fileSystem.getPath(playlistsDir)
        Files.createDirectories(baseDir)
        val rootPath = copyResource(resourceName, baseDir.resolve(resourceName.substringAfterLast('/')))
        val tracks = materializeTracks(resourceName, rootPath, baseDir)
        return PlaylistFixture(fileSystem, rootPath, baseDir, tracks)
    }

    /**
     * Materializes a virtual audio file for [trackInfo] on the given [fileSystem]
     * and returns the resulting [Path]. Useful when tests need to extend a fixture
     * with extra tracks.
     */
    fun materializeTrack(trackInfo: TrackInfo, fileSystem: FileSystem): Path {
        val targetPath = fileSystem.getPath(trackInfo.targetPath)
        val attributes = trackInfo.toAttributes()
        return VirtualFiles.createAt(targetPath, attributes, trackInfo.tagType, fileSystem)
    }

    private fun copyResource(resourceName: String, targetPath: Path): Path {
        val bytes = readResourceBytes(resourceName)
        Files.createDirectories(targetPath.parent)
        Files.write(targetPath, bytes)
        return targetPath
    }

    private fun readResourceBytes(resourceName: String): ByteArray {
        val cleanName = resourceName.trimStart('/')
        val classpath = if (cleanName.startsWith("m3u/")) "/$cleanName" else "/m3u/$cleanName"
        val stream =
            M3uTestFixtures::class.java.getResourceAsStream(classpath)
                ?: error("M3U resource not found on classpath: $classpath")
        return stream.use { it.readBytes() }
    }

    private fun materializeTracks(
        rootResource: String,
        rootM3uOnFs: Path,
        baseDir: Path
    ): List<TrackInfo> {
        val visited = mutableSetOf<String>()
        val collected = mutableListOf<TrackInfo>()
        materializeRecursive(rootResource, rootM3uOnFs, baseDir, visited, collected)
        return collected.toList()
    }

    private fun materializeRecursive(
        resourceName: String,
        m3uOnFs: Path,
        baseDir: Path,
        visited: MutableSet<String>,
        collected: MutableList<TrackInfo>
    ) {
        if (!visited.add(resourceName)) return
        val entries = M3uResourceParser.parse(readResourceBytes(resourceName))
        for (entry in entries) {
            val resolved = m3uOnFs.parent.resolve(entry.rawPath).normalize()
            if (entry.isNestedPlaylist) {
                val nestedResource = resolveNestedResource(resourceName, entry.rawPath)
                Files.createDirectories(resolved.parent)
                copyResource(nestedResource, resolved)
                materializeRecursive(nestedResource, resolved, baseDir, visited, collected)
            } else {
                val info = TrackInfo.from(entry, resolved)
                VirtualFiles.createAt(resolved, info.toAttributes(), info.tagType, baseDir.fileSystem)
                collected.add(info)
            }
        }
    }

    private fun resolveNestedResource(parentResource: String, nestedRawPath: String): String {
        val parentDir = parentResource.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parentDir.isEmpty()) nestedRawPath else "$parentDir/$nestedRawPath"
    }

    /**
     * Loaded playlist fixture: the in-memory filesystem, the root playlist path,
     * the base directory the playlist lives in, and the materialized track metadata.
     */
    data class PlaylistFixture(
        val fileSystem: FileSystem,
        val rootPath: Path,
        val baseDir: Path,
        val tracks: List<TrackInfo>
    )

    /**
     * Metadata extracted from a single M3U entry along with the virtual file's location.
     */
    data class TrackInfo(
        val targetPath: String,
        val artist: String,
        val albumArtist: String,
        val album: String,
        val title: String,
        val trackNumber: Short,
        val tagType: AudioFileTagType
    ) {
        fun toAttributes(): AudioItemTestAttributes {
            val artistEntity = ImmutableArtist.of(artist)
            val albumArtistEntity = ImmutableArtist.of(albumArtist)
            return io.kotest.property.Arb.audioAttributes(
                artist = artistEntity,
                album = ImmutableAlbum(album, albumArtistEntity),
                title = title,
                trackNumber = trackNumber
            ).next()
        }

        companion object {
            private val EXTENSION_TO_TAG_TYPE =
                mapOf(
                    "mp3" to AudioFileTagType.ID3_V_24,
                    "flac" to AudioFileTagType.FLAC,
                    "m4a" to AudioFileTagType.MP4_INFO,
                    "wav" to AudioFileTagType.WAV,
                    "ogg" to AudioFileTagType.VORBIS_COMMENT
                )

            fun from(entry: ParsedEntry, resolvedPath: Path): TrackInfo {
                val extension = resolvedPath.fileName.toString().substringAfterLast('.', "").lowercase()
                val tagType =
                    EXTENSION_TO_TAG_TYPE[extension]
                        ?: error("Unsupported audio extension for fixture: $extension (${AudioFileType.entries})")
                val fromPath = derivePathMetadata(resolvedPath)
                return TrackInfo(
                    targetPath = resolvedPath.toString(),
                    artist = entry.extArtist ?: fromPath.artist,
                    albumArtist = fromPath.albumArtist,
                    album = entry.extAlbum ?: fromPath.album,
                    title = entry.extTitle ?: fromPath.title,
                    trackNumber = fromPath.trackNumber,
                    tagType = tagType
                )
            }

            private fun derivePathMetadata(resolved: Path): PathMetadata {
                val fileName = resolved.fileName.toString()
                val stem = fileName.substringBeforeLast('.')
                val match = TRACK_PREFIX.find(stem)
                val trackNumber = match?.groupValues?.get(1)?.toShortOrNull() ?: 1.toShort()
                val title = match?.groupValues?.get(2)?.trim() ?: stem
                val album = resolved.parent?.fileName?.toString() ?: "Unknown Album"
                val grandparent = resolved.parent?.parent?.fileName?.toString() ?: "Unknown Artist"
                val isCompilation = grandparent.equals(COMPILATIONS_LABEL, ignoreCase = true)
                val artist = if (isCompilation) COMPILATION_ARTIST else grandparent
                val albumArtist = if (isCompilation) COMPILATION_ARTIST else grandparent
                return PathMetadata(artist, albumArtist, album, title, trackNumber)
            }

            private val TRACK_PREFIX = Regex("""^(\d{1,3})\s+(.+)$""")
        }

        private data class PathMetadata(
            val artist: String,
            val albumArtist: String,
            val album: String,
            val title: String,
            val trackNumber: Short
        )
    }

    /** A single entry parsed from a static M3U resource. */
    data class ParsedEntry(
        val rawPath: String,
        val isNestedPlaylist: Boolean,
        val extTitle: String?,
        val extArtist: String?,
        val extAlbum: String?
    )

    /**
     * Reads a static M3U resource bytes and produces ordered [ParsedEntry]s. Used
     * to discover what virtual files a fixture must materialize and to recurse
     * into nested M3U references.
     */
    private object M3uResourceParser {

        fun parse(bytes: ByteArray): List<ParsedEntry> {
            val text = decode(bytes)
            val entries = mutableListOf<ParsedEntry>()
            var pendingTitle: String? = null
            var pendingArtist: String? = null
            var pendingAlbum: String? = null
            text.lineSequence().forEach { rawLine ->
                val line = rawLine.removePrefix(BOM_STRING).trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTINF:") -> pendingTitle = extractExtInfTitle(line)
                    line.startsWith("#EXTART:") -> pendingArtist = line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
                    line.startsWith("#EXTALB:") -> pendingAlbum = line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
                    line.startsWith("#") -> Unit
                    else -> {
                        val isNested = line.endsWith(".m3u", ignoreCase = true) || line.endsWith(".m3u8", ignoreCase = true)
                        entries.add(
                            ParsedEntry(
                                rawPath = line,
                                isNestedPlaylist = isNested,
                                extTitle = pendingTitle,
                                extArtist = pendingArtist,
                                extAlbum = pendingAlbum
                            )
                        )
                        pendingTitle = null
                        pendingArtist = null
                        pendingAlbum = null
                    }
                }
            }
            return entries
        }

        private fun decode(bytes: ByteArray): String =
            try {
                StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: Exception) {
                java.nio.charset.Charset.forName("windows-1252")
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString()
            }

        private fun extractExtInfTitle(line: String): String? {
            val payload = line.substringAfter("#EXTINF:")
            val comma = payload.indexOf(',')
            if (comma < 0) return null
            return payload.substring(comma + 1).trim().takeIf { it.isNotEmpty() }
        }

        private fun String.lineSequence(): Sequence<String> = splitToSequence('\n').map { it.removeSuffix("\r") }

        private const val BOM_STRING = "\uFEFF"
    }
}