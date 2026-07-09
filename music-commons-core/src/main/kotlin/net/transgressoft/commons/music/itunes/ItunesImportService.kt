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

package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.MusicLibrary
import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioMetadataIO
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.JAudioTaggerMetadataIO
import net.transgressoft.commons.music.audio.Label
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.parseGenre
import net.transgressoft.commons.music.playlist.ReactiveAudioPlaylist
import mu.KotlinLogging
import org.slf4j.MDC
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.io.path.extension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.future
import kotlinx.coroutines.slf4j.MDCContext

/**
 * Orchestrates importing tracks and playlists from a parsed [ItunesLibrary] into a [MusicLibrary].
 *
 * The import follows a two-step flow: the consumer first parses the XML via [ItunesLibraryParser],
 * optionally selects a subset of playlists, then calls [importAsync] with the selection and a
 * configured [ItunesImportPolicy]. Every track present in [ItunesLibrary.tracks] is considered for
 * import regardless of the playlist selection; the selection only controls which playlists (and
 * their ancestor folders) are recreated in the destination library.
 *
 * Track import behavior depends on [ItunesImportPolicy.useFileMetadata]:
 * - When `true`, audio items are created via [MusicLibrary.audioItemFromFile] which reads all
 *   metadata from file tags.
 * - When `false`, only technical metadata (bitrate, duration, encoder) is read from file tags.
 *   User-facing fields (title, artist, album, genre) come from the iTunes data.
 *
 * When a playlist name already exists in the hierarchy, the import creates it with an
 * incremental suffix (e.g., `Playlist_1`, `Playlist_2`). Folder playlists support recursive nesting.
 *
 * Hierarchy preservation: when a selected playlist references an ancestor folder via
 * [ItunesPlaylist.parentPersistentId] but the ancestor is not itself in [selectedPlaylists],
 * the missing ancestors are looked up in [ItunesLibrary.playlists] and imported automatically
 * so the user's original folder structure is reproduced in the target library.
 *
 * @param musicLibrary The target library to import into.
 */
class ItunesImportService<I, P>
    @JvmOverloads
    constructor(
        private val musicLibrary: MusicLibrary<I, P>,
        private val metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO(),
        private val fileSystem: FileSystem = FileSystems.getDefault(),
        private val instanceName: String = ""
    ) : AutoCloseable where I : ReactiveAudioItem<I>,
          P : ReactiveAudioPlaylist<I, P> {

    private val logger = KotlinLogging.logger {}
    private val serviceJob: Job = SupervisorJob()
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.IO + serviceJob)

    internal var trackResolver: ItunesTrackResolver = ItunesTrackResolver(fileSystem)
    internal var playlistBuilder: ItunesPlaylistBuilder<I, P> = ItunesPlaylistBuilder(musicLibrary)

    /**
     * Imports every track from [itunesLibrary] into the [MusicLibrary], applying the given [policy],
     * and recreates the playlists named in [selectedPlaylists] (plus their ancestor folders).
     *
     * The imported track set is always the full content of [ItunesLibrary.tracks]; [selectedPlaylists]
     * does not narrow it. Tracks that cannot be resolved (missing local file, unsupported extension,
     * read error) are reported in [ImportResult.unresolved].
     *
     * Returns a [CompletableFuture] that completes with an [ImportResult] summarizing the import.
     * The future can be canceled via [CompletableFuture.cancel], which stops processing between tracks.
     *
     * @param selectedPlaylists Playlists chosen by the consumer for recreation in the destination
     *  library. Does not narrow the set of imported tracks — every track in [itunesLibrary] is
     *  considered for import.
     * @param itunesLibrary The full parsed iTunes library; every entry in [ItunesLibrary.tracks]
     *  is processed.
     * @param policy Import configuration controlling metadata source, play count, write-back, etc.
     * @param rootDirectoryName Optional name of an existing playlist directory to use as the
     *  parent for top-level imported playlists (those whose iTunes
     *  [ItunesPlaylist.parentPersistentId] is `null`). When provided, every top-level imported
     *  playlist is wired as a child of the named directory. When `null` or when no playlist
     *  with that name exists in the library, top-level imported playlists remain orphaned in
     *  the hierarchy and the consumer is responsible for placing them.
     * @param onProgress Callback invoked after each track is processed.
     * @return A cancellable future completing with the import result.
     */
    fun importAsync(
        selectedPlaylists: List<ItunesPlaylist>,
        itunesLibrary: ItunesLibrary,
        policy: ItunesImportPolicy = ItunesImportPolicy(),
        rootDirectoryName: String? = null,
        onProgress: (ImportProgress) -> Unit = {}
    ): CompletableFuture<ImportResult> {
        val sessionId = UUID.randomUUID().toString()
        MDC.put("importSessionId", sessionId)
        if (instanceName.isNotEmpty()) MDC.put("libraryInstance", instanceName)
        try {
            return serviceScope.future(MDCContext()) {
                val effectivePlaylists = playlistBuilder.expandWithAncestors(selectedPlaylists, itunesLibrary)
                val trackImportResult = importTracks(itunesLibrary, policy, onProgress)
                val rejectedNames = playlistBuilder.createPlaylists(effectivePlaylists, trackImportResult.trackIdToItem, rootDirectoryName)

                ImportResult(
                    imported = trackImportResult.imported.toList(),
                    unresolved = trackImportResult.unresolved.toList(),
                    rejectedPlaylistNames = rejectedNames
                ).also {
                    logger.debug {
                        "iTunes import complete: ${trackImportResult.imported.size} imported, " +
                            "${trackImportResult.unresolved.size} unresolved, " +
                            "${rejectedNames.size} playlists rejected"
                    }
                }
            }
        } finally {
            MDC.remove("importSessionId")
            if (instanceName.isNotEmpty()) MDC.remove("libraryInstance")
        }
    }

    /**
     * Releases the coroutine resources backing [importAsync]. After close, async imports
     * will fail; the underlying music library is not affected.
     */
    override fun close() {
        serviceScope.cancel()
    }

    private suspend fun importTracks(
        itunesLibrary: ItunesLibrary,
        policy: ItunesImportPolicy,
        onProgress: (ImportProgress) -> Unit
    ): TrackImportAccumulator {
        val accumulator = TrackImportAccumulator(itunesLibrary.tracks.size)

        for ((trackId, track) in itunesLibrary.tracks) {
            currentCoroutineContext().ensureActive()
            processTrack(track, trackId, policy, accumulator)
            accumulator.itemsProcessed++
            onProgress(ImportProgress(accumulator.itemsProcessed, accumulator.totalItems, track.location))
        }

        return accumulator
    }

    private fun processTrack(track: ItunesTrack, trackId: Int, policy: ItunesImportPolicy, accumulator: TrackImportAccumulator) {
        try {
            val path = trackResolver.resolveTrackPath(track)

            if (trackResolver.isUnsupportedFileType(path, policy)) {
                logger.trace { "Skipping track '${track.title}': unsupported file type '${path.extension.lowercase()}'" }
                accumulator.unresolved.add(UnresolvedTrack(path, track.title, UnresolvedReason.UnsupportedType(path.extension.lowercase())))
                return
            }

            if (!Files.exists(path)) {
                logger.trace { "Track '${track.title}': file not found at $path" }
                accumulator.unresolved.add(UnresolvedTrack(path, track.title, UnresolvedReason.FileNotFound))
                return
            }

            val audioItem = importTrack(track, path, policy)
            accumulator.trackIdToItem[trackId] = audioItem
            accumulator.imported.add(audioItem)
            logger.trace { "Imported track '${track.title}' with id ${audioItem.id}" }
        } catch (e: CancellationException) {
            // Cooperative cancellation must propagate; recording it as an UnresolvedTrack would
            // contradict importAsync's "stops processing between tracks" contract.
            throw e
        } catch (e: Exception) {
            logger.error("Error importing track '${track.title}'", e)
            accumulator.unresolved.add(UnresolvedTrack(null, track.title, UnresolvedReason.ImportError(e.message ?: "Unknown error")))
        }
    }

    private fun importTrack(track: ItunesTrack, path: Path, policy: ItunesImportPolicy): I {
        val audioItem = musicLibrary.audioItemFromFile(path)

        if (!policy.useFileMetadata) {
            applyItunesMetadata(audioItem, track)
        }

        if (policy.holdPlayCount && track.playCount > 0) {
            audioItem.setPlayCount(track.playCount)
        }

        if (policy.writeMetadata && !policy.useFileMetadata) {
            metadataIO.writeMetadata(audioItem)
        }

        return audioItem
    }

    private fun applyItunesMetadata(audioItem: I, track: ItunesTrack) {
        val (artist, album, genres) = resolveItunesMetadata(track)
        audioItem.mutate {
            title = track.title
            this.artist = artist
            this.album = album
            this.genres = genres
            comments = track.comments
            trackNumber = track.trackNumber
            discNumber = track.discNumber
            bpm = track.bpm
        }
    }

    private fun resolveItunesMetadata(track: ItunesTrack): ItunesMetadata {
        val artist = Artist.of(track.artist)
        val albumArtist = Artist.of(track.albumArtist)
        val album =
            AlbumDetails(
                name = track.album,
                albumArtist = albumArtist,
                isCompilation = track.isCompilation,
                year = track.year,
                label = Label.UNKNOWN
            )
        val genres = track.genre?.let { parseGenre(it) } ?: emptySet()
        return ItunesMetadata(artist, album, genres)
    }

    private data class ItunesMetadata(val artist: Artist, val album: AlbumDetails, val genres: Set<Genre>)

    private class TrackImportAccumulator(val totalItems: Int) {
        var itemsProcessed = 0
        val imported = mutableListOf<ReactiveAudioItem<*>>()
        val unresolved = mutableListOf<UnresolvedTrack>()
        val trackIdToItem = mutableMapOf<Int, ReactiveAudioItem<*>>()
    }
}