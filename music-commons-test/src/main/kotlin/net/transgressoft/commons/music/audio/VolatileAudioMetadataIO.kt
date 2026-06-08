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

package net.transgressoft.commons.music.audio

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * Filesystem-aware in-memory [AudioMetadataIO] for tests.
 *
 * Stores metadata and cover bytes in maps keyed by [Path], with no dependency on JAudioTagger or
 * any other backend library. Tests stub by calling [stub] / [stubCover], or use [createVirtualFile]
 * to both stub the in-memory state and materialize an empty file on the wrapped [FileSystem] so
 * that consumer code which calls `Files.exists(path)` sees the path.
 *
 * The wrapped [FileSystem] defaults to the JVM default — for jimfs-based test suites, pass the
 * jimfs filesystem to keep created files isolated to the spec lifetime.
 *
 * Specs that exercise real-file round-trips (e.g. MutableAudioItemTest, waveform tests against
 * fixtures on disk) should inject [JAudioTaggerMetadataIO] directly instead of this fake — there
 * is no fallback from this class to JAudioTagger.
 *
 * Example:
 * ```
 * val io = VolatileAudioMetadataIO(jimfsFileSystem)
 * io.createVirtualFile(
 *     path = jimfsFileSystem.getPath("/music/song.mp3"),
 *     metadata = AudioItemMetadata(title = "Song", artist = ImmutableArtist.of("Artist"))
 * )
 * ```
 */
class VolatileAudioMetadataIO(val fileSystem: FileSystem = FileSystems.getDefault()) : AudioMetadataIO {

    private val metadata: MutableMap<Path, AudioItemMetadata> = mutableMapOf()
    private val covers: MutableMap<Path, ByteArray?> = mutableMapOf()

    /**
     * Stubs the [AudioItemMetadata] returned by [readMetadata] for [path]. Paths that are not
     * stubbed return a default [AudioItemMetadata].
     */
    fun stub(path: Path, metadata: AudioItemMetadata) {
        this.metadata[path] = metadata
    }

    /**
     * Stubs the raw cover bytes returned by [loadCover] for [path]. Pass `null` to simulate a file
     * without artwork. Stored as a defensive copy to keep fixture state immune to caller mutation.
     */
    fun stubCover(path: Path, bytes: ByteArray?) {
        covers[path] = bytes?.copyOf()
    }

    /**
     * Materializes an empty file at [path] on the wrapped [FileSystem] and stubs the metadata /
     * cover bytes returned by [readMetadata] / [loadCover].
     *
     * Convenience helper for the common test pattern of needing both `Files.exists(path)` to
     * return `true` and the metadata seam to return a known payload.
     */
    fun createVirtualFile(path: Path, metadata: AudioItemMetadata = AudioItemMetadata(), cover: ByteArray? = null) {
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, byteArrayOf())
        stub(path, metadata)
        stubCover(path, cover)
    }

    override fun readMetadata(path: Path): AudioItemMetadata = metadata[path] ?: AudioItemMetadata()

    override fun loadCover(path: Path): ByteArray? = covers[path]?.copyOf()

    override fun writeMetadata(item: ReactiveAudioItem<*>) {
        val current = metadata[item.path] ?: AudioItemMetadata()
        metadata[item.path] =
            current.copy(
                title = item.title,
                artist = item.artist,
                album = item.album,
                genres = item.genres,
                comments = item.comments,
                trackNumber = item.trackNumber,
                discNumber = item.discNumber,
                bpm = item.bpm,
                encoder = item.encoder
            )
        // Mirror writeMetadata semantics: a null cover clears the previously stubbed bytes, so
        // tests can exercise cover removal. Defensive copy keeps fixture state immune to caller
        // mutation of the source array.
        covers[item.path] = item.coverImageBytes?.copyOf()
    }
}