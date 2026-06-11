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

import net.transgressoft.commons.music.MusicLibrary
import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.playlist.ReactiveAudioPlaylist
import net.transgressoft.lirp.entity.toIds
import mu.KotlinLogging
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

/**
 * Exception thrown when an unrecoverable error occurs during M3U playlist import.
 */
class M3uImportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    constructor(message: String) : this(message, null)
}

/**
 * Exception thrown when an unrecoverable error occurs during M3U playlist parsing.
 *
 * Typical causes include missing files, non-regular file paths, or unreadable content.
 */
class M3uParseException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    constructor(message: String) : this(message, null)
}

/**
 * Exception thrown when a recursive playlist inclusion cycle is detected during M3U import.
 *
 * The message includes the cycle path for debugging (e.g.,
 * `"Cycle detected: /music/A.m3u -> /music/B.m3u -> /music/A.m3u"`).
 */
class M3uCycleException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    constructor(message: String) : this(message, null)
}

/**
 * Orchestrates importing tracks from an M3U playlist file into a [MusicLibrary].
 *
 * Parses the M3U file via [M3uParser], creates [ReactiveAudioItem] instances for each resolved
 * path, assembles them into a [ReactiveAudioPlaylist], and recursively imports nested
 * playlist references (`.m3u` / `.m3u8`).
 *
 * Tracks referencing extensions outside [AudioFileType] are skipped with a warning,
 * as are unreadable files; the import continues with the remaining tracks. Playlist
 * name collisions are detected up-front so the library is never left in a partially
 * imported state on failure.
 *
 * @param I the concrete audio item type, mirroring [MusicLibrary]'s `I` bound
 * @param P the concrete playlist type, mirroring [MusicLibrary]'s `P` bound
 * @param musicLibrary the target library to import into
 * @param maxDepth maximum nested playlist depth, with the root playlist at depth 0
 */
class M3uImportService<I : ReactiveAudioItem<I>, P : ReactiveAudioPlaylist<I, P>>(
    private val musicLibrary: MusicLibrary<I, P>,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH
) : AutoCloseable {

    init {
        require(maxDepth >= 0) { "maxDepth must be non-negative, got $maxDepth" }
    }

    private val logger = KotlinLogging.logger {}
    private val serviceJob: Job = SupervisorJob()
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.IO + serviceJob)

    /**
     * Imports the M3U playlist at [rootM3u] into the library.
     *
     * Detects and prevents recursive playlist inclusion cycles using a visited-stack
     * with `toRealPath()` canonicalization. When a cycle is detected, a
     * [M3uCycleException] is thrown with the cycle path for debugging. Playlist name
     * collisions are detected up-front so a partial import can never leak entries
     * into the library.
     *
     * @param rootM3u path to the M3U or M3U8 file
     * @return a fully populated [P] with all referenced audio items
     * @throws M3uCycleException if a recursive playlist inclusion cycle is detected
     * @throws M3uImportException if the configured recursion depth limit is exceeded
     *         or a derived playlist name collides with an existing playlist
     * @throws M3uParseException if the root M3U file cannot be parsed
     */
    fun import(rootM3u: Path): P {
        val plan = planImport(rootM3u, visited = emptyList(), depth = 0)
        rejectCollisions(plan)
        return materialize(plan, existingAudioItemsByPath())
    }

    /**
     * Asynchronously imports the M3U playlist at [rootM3u] using [Dispatchers.IO].
     *
     * @param rootM3u path to the M3U or M3U8 file
     * @return a [CompletableFuture] completing with the imported playlist. Cancelling
     *         the future propagates cancellation to the underlying coroutine.
     */
    fun importAsync(rootM3u: Path): CompletableFuture<P> = importAsync(rootM3u, Dispatchers.IO)

    /**
     * Asynchronously imports the M3U playlist at [rootM3u] using [dispatcher].
     *
     * @param rootM3u path to the M3U or M3U8 file
     * @param dispatcher coroutine dispatcher used for the import work
     * @return a [CompletableFuture] completing with the imported playlist. Cancelling
     *         the future propagates cancellation to the underlying coroutine.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun importAsync(rootM3u: Path, dispatcher: CoroutineDispatcher): CompletableFuture<P> {
        val deferred = serviceScope.async(dispatcher) { import(rootM3u) }
        val future = CompletableFuture<P>()
        deferred.invokeOnCompletion { error ->
            if (error == null) {
                future.complete(deferred.getCompleted())
            } else {
                future.completeExceptionally(error)
            }
        }
        future.whenComplete { _, _ -> if (future.isCancelled) deferred.cancel() }
        return future
    }

    /**
     * Releases the coroutine resources backing [importAsync]. After close, async imports
     * will fail; synchronous imports remain available.
     */
    override fun close() {
        serviceScope.cancel()
    }

    private fun planImport(
        m3uPath: Path,
        visited: List<Path>,
        depth: Int
    ): PlannedPlaylist {
        if (depth > maxDepth) {
            throw M3uImportException("M3U import exceeded maximum depth $maxDepth: ${m3uPath.toAbsolutePath()}")
        }
        val canonicalPath = canonicalize(m3uPath)
        if (canonicalPath in visited) {
            val cyclePath = (visited + listOf(canonicalPath)).joinToString(" -> ")
            throw M3uCycleException("Cycle detected: $cyclePath")
        }
        val baseDir = m3uPath.parent ?: m3uPath.toAbsolutePath().parent
        val parseResult = M3uParser(baseDir).parse(m3uPath)
        val name = playlistName(m3uPath)
        val children =
            parseResult.nestedPlaylists.mapNotNull { nested ->
                planNested(nested, visited + listOf(canonicalPath), depth + 1)
            }
        return PlannedPlaylist(name, parseResult.entries.map { it.resolvedPath }, children)
    }

    private fun planNested(
        nestedPath: Path,
        visited: List<Path>,
        depth: Int
    ): PlannedPlaylist? =
        try {
            planImport(nestedPath, visited, depth)
        } catch (e: NoSuchFileException) {
            logger.warn(e) { "Skipping missing nested M3U playlist: ${nestedPath.toAbsolutePath()}" }
            null
        } catch (e: M3uParseException) {
            logger.warn(e) { "Skipping unreadable nested M3U playlist: ${nestedPath.toAbsolutePath()}" }
            null
        } catch (e: IOException) {
            logger.warn(e) { "Skipping unreadable nested M3U playlist: ${nestedPath.toAbsolutePath()}" }
            null
        }

    private fun rejectCollisions(plan: PlannedPlaylist) {
        val names = plan.walk().map { it.name }.toList()
        names.groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { (_, count) -> count > 1 }
            ?.let { (name, _) ->
                throw M3uImportException("Imported tree contains duplicate playlist name '$name'")
            }
        names.firstOrNull { musicLibrary.findPlaylistByName(it).isPresent }?.let { name ->
            throw M3uImportException("Playlist with name '$name' already exists")
        }
    }

    private fun materialize(
        plan: PlannedPlaylist,
        audioItemsByPath: MutableMap<Path, I>
    ): P {
        val children = plan.children.map { materialize(it, audioItemsByPath) }
        val audioItems = plan.audioPaths.mapNotNull { resolveAudioItem(it, audioItemsByPath) }
        val playlist = musicLibrary.createPlaylist(plan.name, audioItems.toIds())
        if (children.isNotEmpty()) {
            playlist.isDirectory = true
            children.forEach { playlist.addPlaylist(it) }
        }
        return playlist
    }

    private fun canonicalize(m3uPath: Path): Path =
        try {
            m3uPath.toRealPath()
        } catch (e: IOException) {
            throw M3uParseException("Cannot resolve M3U file '${m3uPath.toAbsolutePath()}'", e)
        }

    private fun playlistName(m3uPath: Path): String = m3uPath.fileName.toString().substringBeforeLast('.')

    private fun resolveAudioItem(path: Path, audioItemsByPath: MutableMap<Path, I>): I? {
        if (isUnsupportedFileType(path)) {
            logger.warn { "Skipping unsupported audio file type: ${path.toAbsolutePath()}" }
            return null
        }
        val normalizedPath = path.toAbsolutePath().normalize()
        audioItemsByPath[normalizedPath]?.let { return it }
        return try {
            musicLibrary.audioItemFromFile(path).also { audioItem ->
                audioItemsByPath[audioItem.path.toAbsolutePath().normalize()] = audioItem
                audioItemsByPath[normalizedPath] = audioItem
            }
        } catch (e: Exception) {
            logger.warn(e) { "Skipping unreadable file: ${path.toAbsolutePath()}" }
            null
        }
    }

    private fun isUnsupportedFileType(path: Path): Boolean {
        val extension = path.fileName?.toString()?.substringAfterLast('.', missingDelimiterValue = "").orEmpty()
        return AudioFileType.fromExtension(extension) == null
    }

    private fun existingAudioItemsByPath(): MutableMap<Path, I> =
        musicLibrary.audioLibrary().associateByTo(mutableMapOf()) { it.path.toAbsolutePath().normalize() }

    private data class PlannedPlaylist(
        val name: String,
        val audioPaths: List<Path>,
        val children: List<PlannedPlaylist>
    ) {
        fun walk(): Sequence<PlannedPlaylist> =
            sequence {
                yield(this@PlannedPlaylist)
                children.forEach { yieldAll(it.walk()) }
            }
    }

    companion object {
        /** Default maximum nested playlist depth. */
        const val DEFAULT_MAX_DEPTH: Int = 32
    }
}