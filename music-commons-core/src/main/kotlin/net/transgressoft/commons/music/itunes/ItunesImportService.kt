package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.MusicLibrary
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.ImmutableLabel
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.WindowsPathException
import net.transgressoft.commons.music.audio.WindowsViolation
import net.transgressoft.commons.music.common.WindowsPathValidator
import mu.KotlinLogging
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.Normalizer
import java.util.concurrent.CompletableFuture
import kotlin.io.path.extension
import kotlinx.coroutines.CancellationException
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
class ItunesImportService(private val musicLibrary: MusicLibrary<*, *>) {

    private val logger = KotlinLogging.logger {}

    /**
     * Imports tracks from [selectedPlaylists] (and their referenced tracks from [itunesLibrary])
     * into the [MusicLibrary], applying the given [policy].
     *
     * Returns a [CompletableFuture] that completes with an [ImportResult] summarizing the import.
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
    ): CompletableFuture<ImportResult> =
        CoroutineScope(Dispatchers.IO).future {
            val trackImportResult = importTracks(selectedPlaylists, itunesLibrary, policy, onProgress)
            val rejectedNames = createPlaylists(selectedPlaylists, trackImportResult.trackIdToItem)

            ImportResult(
                imported = trackImportResult.imported.toList(),
                unresolved = trackImportResult.unresolved.toList(),
                rejectedPlaylistNames = rejectedNames
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
                accumulator.unresolved.add(UnresolvedTrack(path, track.title, UnresolvedReason.UnsupportedType(path.extension.lowercase())))
                return
            }

            if (!Files.exists(path)) {
                logger.debug { "Track '${track.title}': file not found at $path" }
                accumulator.unresolved.add(UnresolvedTrack(path, track.title, UnresolvedReason.FileNotFound))
                return
            }

            val audioItem = importTrack(track, path, policy)
            accumulator.trackIdToItem[trackId] = audioItem
            accumulator.imported.add(audioItem)
            logger.debug { "Imported track '${track.title}' with id ${audioItem.id}" }
        } catch (e: CancellationException) {
            // Cooperative cancellation must propagate; recording it as an UnresolvedTrack would
            // contradict importAsync's "stops processing between tracks" contract.
            throw e
        } catch (e: Exception) {
            logger.error("Error importing track '${track.title}'", e)
            accumulator.unresolved.add(UnresolvedTrack(null, track.title, UnresolvedReason.ImportError(e.message ?: "Unknown error")))
        }
    }

    private fun isUnsupportedFileType(path: Path, policy: ItunesImportPolicy): Boolean {
        val extension = path.extension.lowercase()
        val fileType = AudioFileType.entries.find { it.extension == extension }
        return fileType == null || fileType !in policy.acceptedFileTypes
    }

    private suspend fun importTrack(track: ItunesTrack, path: Path, policy: ItunesImportPolicy): ReactiveAudioItem<*> {
        val audioItem = musicLibrary.audioItemFromFile(path)

        if (!policy.useFileMetadata) {
            applyItunesMetadata(audioItem, track)
        }

        if (policy.holdPlayCount && track.playCount > 0) {
            audioItem.setPlayCount(track.playCount)
        }

        if (policy.writeMetadata && !policy.useFileMetadata) {
            audioItem.writeMetadata().join()
        }

        return audioItem
    }

    private fun applyItunesMetadata(audioItem: ReactiveAudioItem<*>, track: ItunesTrack) {
        val (artist, album, genres) = resolveItunesMetadata(track)
        audioItem.withEventsDisabled {
            audioItem.title = track.title
            audioItem.artist = artist
            audioItem.album = album
            audioItem.genres = genres
            audioItem.comments = track.comments
            audioItem.trackNumber = track.trackNumber
            audioItem.discNumber = track.discNumber
            audioItem.bpm = track.bpm
        }
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
        val rawPath = Paths.get(uri)
        // Normalize filename to NFC: macOS iTunes writes NFD-decomposed Unicode;
        // Linux/Windows expect NFC. Idempotent and no-op for ASCII.
        val normalizedName = Normalizer.normalize(rawPath.fileName.toString(), Normalizer.Form.NFC)
        return rawPath.parent?.resolve(normalizedName) ?: rawPath
    }

    private fun createPlaylists(
        selectedPlaylists: List<ItunesPlaylist>,
        trackIdToItem: Map<Int, ReactiveAudioItem<*>>
    ): List<RejectedPlaylistName> {
        val createdNameByPersistentId = mutableMapOf<String, String>()
        val rejected = mutableListOf<RejectedPlaylistName>()

        createFolderDirectories(selectedPlaylists, createdNameByPersistentId, rejected)
        createRegularPlaylists(selectedPlaylists, trackIdToItem, createdNameByPersistentId, rejected)
        wirePlaylistHierarchy(selectedPlaylists, createdNameByPersistentId)

        return rejected
    }

    private fun createFolderDirectories(
        selectedPlaylists: List<ItunesPlaylist>,
        createdNameByPersistentId: MutableMap<String, String>,
        rejected: MutableList<RejectedPlaylistName>
    ) {
        for (playlist in selectedPlaylists) {
            if (!playlist.isFolder) continue
            if (!acceptPlaylistName(playlist.name, rejected)) continue
            val uniqueName = resolveUniqueName(playlist.name)
            musicLibrary.createPlaylistDirectory(uniqueName)
            createdNameByPersistentId[playlist.persistentId] = uniqueName
            logger.debug { "Created playlist directory '$uniqueName'" }
        }
    }

    private fun createRegularPlaylists(
        selectedPlaylists: List<ItunesPlaylist>,
        trackIdToItem: Map<Int, ReactiveAudioItem<*>>,
        createdNameByPersistentId: MutableMap<String, String>,
        rejected: MutableList<RejectedPlaylistName>
    ) {
        for (playlist in selectedPlaylists) {
            if (playlist.isFolder) continue
            if (!acceptPlaylistName(playlist.name, rejected)) continue
            val audioItemIds = playlist.trackIds.mapNotNull { trackIdToItem[it]?.id }
            val uniqueName = resolveUniqueName(playlist.name)
            musicLibrary.createPlaylist(uniqueName, audioItemIds)
            createdNameByPersistentId[playlist.persistentId] = uniqueName
            logger.debug { "Created playlist '$uniqueName' with ${audioItemIds.size} items" }
        }
    }

    private fun acceptPlaylistName(name: String, rejected: MutableList<RejectedPlaylistName>): Boolean =
        try {
            WindowsPathValidator.validateName(name)
            true
        } catch (e: WindowsPathException) {
            rejected.add(RejectedPlaylistName(name, e.violation.toRejectionReason()))
            false
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

    private fun WindowsViolation.toRejectionReason(): RejectionReason =
        when (this) {
            is WindowsViolation.ForbiddenChar -> RejectionReason.ForbiddenChar(char)
            is WindowsViolation.ReservedName -> RejectionReason.ReservedName
            WindowsViolation.TrailingDotOrSpace -> RejectionReason.TrailingDotOrSpace
            // Today validateName(name) does not enforce MAX_PATH on a single segment, so this branch is
            // currently unreachable. Mapped exhaustively (rather than throwing) so any future tightening
            // of the validator surfaces as a typed rejection instead of a hard import failure.
            WindowsViolation.ExceedsMaxPath -> RejectionReason.ExceedsMaxPath
        }

    private data class ItunesMetadata(val artist: Artist, val album: Album, val genres: Set<Genre>)

    private class TrackImportAccumulator(val totalItems: Int) {
        var itemsProcessed = 0
        val imported = mutableListOf<ReactiveAudioItem<*>>()
        val unresolved = mutableListOf<UnresolvedTrack>()
        val trackIdToItem = mutableMapOf<Int, ReactiveAudioItem<*>>()
    }
}