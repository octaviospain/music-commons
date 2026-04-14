package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.MusicLibrary
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemMetadataUtils
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.ImmutableLabel
import net.transgressoft.commons.music.audio.MutableAudioItem
import mu.KotlinLogging
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import kotlin.io.path.extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.future

/**
 * Orchestrates importing tracks and playlists from a parsed [ItunesLibrary] into a [MusicLibrary].
 *
 * The import follows a two-step flow: the consumer first parses the XML via [ItunesLibraryParser],
 * selects playlists, then calls [importAsync] with the selection and a configured [ItunesImportPolicy].
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
 * @param musicLibrary The target library to import into.
 */
class ItunesImportService(private val musicLibrary: MusicLibrary) {

    private val logger = KotlinLogging.logger {}

    /**
     * Imports tracks from [selectedPlaylists] (and their referenced tracks from [itunesLibrary])
     * into the [MusicLibrary], applying the given [policy].
     *
     * Returns a [CompletableFuture] that completes with an [ItunesImportResult] summarizing the import.
     * The future can be canceled via [CompletableFuture.cancel], which stops processing between tracks.
     *
     * @param selectedPlaylists Playlists chosen by the consumer for import.
     * @param itunesLibrary The full parsed iTunes library (for track lookup).
     * @param policy Import configuration controlling metadata source, play count, write-back, etc.
     * @param onProgress Callback invoked after each track is processed.
     * @return A cancellable future completing with the import result.
     */
    fun importAsync(
        selectedPlaylists: List<ItunesPlaylist>,
        itunesLibrary: ItunesLibrary,
        policy: ItunesImportPolicy = ItunesImportPolicy(),
        onProgress: (ImportProgress) -> Unit = {}
    ): CompletableFuture<ItunesImportResult> =
        CoroutineScope(Dispatchers.IO).future {
            val trackImportResult = importTracks(selectedPlaylists, itunesLibrary, policy, onProgress)
            val playlistsCreated = createPlaylists(selectedPlaylists, trackImportResult.trackIdToItem)

            ItunesImportResult(
                importedCount = trackImportResult.importedCount,
                skippedCount = trackImportResult.skippedCount,
                errorCount = trackImportResult.errors.size,
                errors = trackImportResult.errors,
                playlistsCreated = playlistsCreated
            )
        }

    private suspend fun importTracks(
        selectedPlaylists: List<ItunesPlaylist>,
        itunesLibrary: ItunesLibrary,
        policy: ItunesImportPolicy,
        onProgress: (ImportProgress) -> Unit
    ): TrackImportAccumulator {
        val uniqueTrackIds = selectedPlaylists.flatMap(ItunesPlaylist::trackIds).toSet()
        val accumulator = TrackImportAccumulator(uniqueTrackIds.size)

        for (trackId in uniqueTrackIds) {
            currentCoroutineContext().ensureActive()
            val track = itunesLibrary.tracks[trackId] ?: continue
            processTrack(track, trackId, policy, accumulator)
            accumulator.itemsProcessed++
            onProgress(ImportProgress(accumulator.itemsProcessed, accumulator.totalItems, track.location))
        }

        return accumulator
    }

    private suspend fun processTrack(track: ItunesTrack, trackId: Int, policy: ItunesImportPolicy, accumulator: TrackImportAccumulator) {
        try {
            val path = resolveTrackPath(track)

            if (isUnsupportedFileType(path, policy)) {
                logger.debug { "Skipping track '${track.title}': unsupported file type '${path.extension.lowercase()}'" }
                accumulator.skippedCount++
                return
            }

            if (!Files.exists(path)) {
                handleMissingFile(track, path, policy, accumulator)
                return
            }

            val audioItem = importTrack(track, path, policy)
            accumulator.trackIdToItem[trackId] = audioItem
            accumulator.importedCount++
            logger.debug { "Imported track '${track.title}' with id ${audioItem.id}" }
        } catch (e: Exception) {
            logger.error("Error importing track '${track.title}'", e)
            accumulator.errors.add(ImportError(track.title, e.message ?: "Unknown error"))
        }
    }

    private fun isUnsupportedFileType(path: Path, policy: ItunesImportPolicy): Boolean {
        val extension = path.extension.lowercase()
        val fileType = AudioFileType.entries.find { it.extension == extension }
        return fileType == null || fileType !in policy.acceptedFileTypes
    }

    private fun handleMissingFile(track: ItunesTrack, path: Path, policy: ItunesImportPolicy, accumulator: TrackImportAccumulator) {
        if (policy.ignoreNotFound) {
            logger.debug { "Skipping track '${track.title}': file not found at $path" }
            accumulator.skippedCount++
        } else {
            accumulator.errors.add(ImportError(track.title, "File not found: $path"))
        }
    }

    private suspend fun importTrack(track: ItunesTrack, path: Path, policy: ItunesImportPolicy): AudioItem {
        val audioItem =
            if (policy.useFileMetadata) {
                musicLibrary.audioItemFromFile(path)
            } else {
                importWithItunesMetadata(track, path, policy)
            }

        if (policy.holdPlayCount && track.playCount > 0) {
            audioItem.setPlayCount(track.playCount)
        }

        if (policy.writeMetadata && !policy.useFileMetadata) {
            audioItem.writeMetadata().join()
        }

        return audioItem
    }

    private fun importWithItunesMetadata(track: ItunesTrack, path: Path, policy: ItunesImportPolicy): AudioItem {
        val fileMetadata = AudioItemMetadataUtils.readMetadata(path)
        val audioLib = musicLibrary.audioLibrary() as DefaultAudioLibrary
        val id = audioLib.nextAudioItemId()
        val (artist, album, genres) = resolveItunesMetadata(track)
        val playCount = if (policy.holdPlayCount) track.playCount else 0.toShort()

        val audioItem =
            MutableAudioItem(
                path = path,
                id = id,
                title = track.title,
                duration = Duration.ofMillis(track.totalTimeMs),
                bitRate = fileMetadata.bitRate,
                artist = artist,
                album = album,
                genres = genres,
                comments = track.comments,
                trackNumber = track.trackNumber,
                discNumber = track.discNumber,
                bpm = track.bpm,
                encoder = fileMetadata.encoder,
                encoding = fileMetadata.encoding,
                dateOfCreation = track.dateAdded ?: LocalDateTime.now(),
                lastDateModified = LocalDateTime.now(),
                playCount = playCount
            )

        musicLibrary.audioLibrary().add(audioItem)
        return audioItem
    }

    private fun resolveItunesMetadata(track: ItunesTrack): ItunesMetadata {
        val artist = ImmutableArtist.of(track.artist)
        val albumArtist = ImmutableArtist.of(track.albumArtist)
        val album =
            ImmutableAlbum(
                name = track.album,
                albumArtist = albumArtist,
                isCompilation = track.isCompilation,
                year = track.year,
                label = ImmutableLabel.UNKNOWN
            )
        val genres = track.genre?.let { Genre.parseGenre(it) } ?: emptySet()
        return ItunesMetadata(artist, album, genres)
    }

    private fun resolveTrackPath(track: ItunesTrack): Path {
        val uri = URI(track.location)
        return Paths.get(uri)
    }

    private fun createPlaylists(selectedPlaylists: List<ItunesPlaylist>, trackIdToItem: Map<Int, AudioItem>): Int {
        val createdNameByPersistentId = mutableMapOf<String, String>()
        var playlistsCreated = 0

        playlistsCreated += createFolderDirectories(selectedPlaylists, createdNameByPersistentId)
        playlistsCreated += createRegularPlaylists(selectedPlaylists, trackIdToItem, createdNameByPersistentId)
        wirePlaylistHierarchy(selectedPlaylists, createdNameByPersistentId)

        return playlistsCreated
    }

    private fun createFolderDirectories(
        selectedPlaylists: List<ItunesPlaylist>,
        createdNameByPersistentId: MutableMap<String, String>
    ): Int {
        var count = 0
        for (playlist in selectedPlaylists) {
            if (!playlist.isFolder) continue
            val uniqueName = resolveUniqueName(playlist.name)
            musicLibrary.createPlaylistDirectory(uniqueName)
            createdNameByPersistentId[playlist.persistentId] = uniqueName
            count++
            logger.debug { "Created playlist directory '$uniqueName'" }
        }
        return count
    }

    private fun createRegularPlaylists(
        selectedPlaylists: List<ItunesPlaylist>,
        trackIdToItem: Map<Int, AudioItem>,
        createdNameByPersistentId: MutableMap<String, String>
    ): Int {
        var count = 0
        for (playlist in selectedPlaylists) {
            if (playlist.isFolder) continue
            val audioItems = playlist.trackIds.mapNotNull { trackIdToItem[it] }
            val uniqueName = resolveUniqueName(playlist.name)
            musicLibrary.createPlaylist(uniqueName, audioItems)
            createdNameByPersistentId[playlist.persistentId] = uniqueName
            count++
            logger.debug { "Created playlist '$uniqueName' with ${audioItems.size} items" }
        }
        return count
    }

    private fun wirePlaylistHierarchy(
        selectedPlaylists: List<ItunesPlaylist>,
        createdNameByPersistentId: Map<String, String>
    ) {
        for (playlist in selectedPlaylists) {
            val parentId = playlist.parentPersistentId ?: continue
            val parentName = createdNameByPersistentId[parentId] ?: continue
            val childName = createdNameByPersistentId[playlist.persistentId] ?: continue
            try {
                musicLibrary.playlistHierarchy().addPlaylistsToDirectory(setOf(childName), parentName)
                logger.debug { "Wired '$childName' to folder '$parentName'" }
            } catch (e: Exception) {
                logger.warn("Could not wire '$childName' to folder '$parentName': ${e.message}")
            }
        }
    }

    private fun resolveUniqueName(name: String): String {
        if (!musicLibrary.findPlaylistByName(name).isPresent) return name
        var suffix = 1
        while (musicLibrary.findPlaylistByName("${name}_$suffix").isPresent) {
            suffix++
        }
        return "${name}_$suffix"
    }

    private data class ItunesMetadata(val artist: Artist, val album: Album, val genres: Set<Genre>)

    private class TrackImportAccumulator(val totalItems: Int) {
        var itemsProcessed = 0
        var importedCount = 0
        var skippedCount = 0
        val errors = mutableListOf<ImportError>()
        val trackIdToItem = mutableMapOf<Int, AudioItem>()
    }
}