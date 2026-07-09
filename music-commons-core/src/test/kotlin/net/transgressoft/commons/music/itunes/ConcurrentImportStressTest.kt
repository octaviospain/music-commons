package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.VolatileAudioMetadataIO
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.m3u.M3uImportService
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.testing.registryIsolation
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.DisplayName
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.lang.ref.WeakReference
import java.nio.file.FileSystem
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

/**
 * Stress test for concurrent iTunes and M3U imports into a single live library over a large
 * virtual-file corpus (jimfs). Runs outside the deterministic default suite and is opt-in
 * via `-PincludeStress=true`.
 *
 * Asserts repository-consistency invariants (no lost or duplicate writes) and verifies that
 * no coroutine scopes or library references leak after both imports complete and the library
 * is closed. Timing is never asserted.
 */
@Tags("stress")
@DisplayName("ConcurrentImportStressTest")
internal class ConcurrentImportStressTest : StringSpec({

    registryIsolation()

    val files = virtualFiles()

    // Corpus constants. The two corpora must be disjoint in uniqueId space, not just in path:
    // uniqueId is derived from the file name (plus duration and bit rate), so distinct parent
    // directories are not enough. Each corpus uses its own file-name prefix ("itunes-"/"m3u-")
    // so no item is deduplicated on add and the expected repository size stays the full sum —
    // making this a clean "no lost or duplicate writes under concurrency" invariant.
    val itunesItemCount = 5_000
    val m3uItemCount = 5_000

    "ConcurrentImportStressTest imports iTunes and M3U concurrently into one library with no lost or duplicate writes and no leaked references" {
        var lib: CoreMusicLibrary? =
            CoreMusicLibrary
                .builder()
                .metadataIO(files.metadataIO)
                .instanceName("stress-library")
                .build()
        var itunesService: ItunesImportService<AudioItem, MutableAudioPlaylist>? =
            ItunesImportService(lib!!, files.metadataIO, files.fileSystem, "stress-library")
        var m3uService: M3uImportService<AudioItem, MutableAudioPlaylist>? =
            M3uImportService(lib!!, instanceName = "stress-library")

        val itunesLibrary = buildItunesCorpus(itunesItemCount, files.fileSystem, files.metadataIO)
        val m3uPath = buildM3uCorpus(m3uItemCount, files.fileSystem, files.metadataIO)

        // Launch both imports concurrently on real IO threads — no reactiveScope, genuine race exposure.
        val itunesPolicy = ItunesImportPolicy(useFileMetadata = true, holdPlayCount = false, writeMetadata = false)
        val itunesFuture: CompletableFuture<ImportResult> =
            itunesService!!.importAsync(itunesLibrary.playlists, itunesLibrary, itunesPolicy)
        val m3uFuture: CompletableFuture<MutableAudioPlaylist> = m3uService!!.importAsync(m3uPath)
        CompletableFuture.allOf(itunesFuture, m3uFuture).get()

        val itunesResult = itunesFuture.get()
        val totalExpected = itunesItemCount + m3uItemCount

        // Invariant 1: all iTunes tracks imported without loss
        itunesResult.unresolved shouldHaveSize 0
        itunesResult.imported shouldHaveSize itunesItemCount

        // Invariant 2: repository size matches the expected unique count
        lib!!.audioLibrary().size() shouldBe totalExpected

        // Invariant 3: no duplicate IDs in the repository
        val allIds = lib!!.audioLibrary().search(totalExpected) { true }.map { it.id }
        allIds.toSet().size shouldBe totalExpected

        // Hold a WeakReference to the audio library component before closing.
        val audioLibraryRef = WeakReference(lib!!.audioLibrary())

        itunesService!!.close()
        m3uService!!.close()
        lib!!.close()

        // Null all local strong references so GC can reclaim the closed audio library.
        itunesService = null
        m3uService = null
        lib = null

        // Invariant 4: no leaked audio library reference after close.
        eventually(10.seconds) {
            System.gc()
            audioLibraryRef.get().shouldBeNull()
        }
    }
})

// -------------------------------------------------------------------------------------------------
// Corpus builders — private helpers that create fully materialized virtual corpora in jimfs.
// -------------------------------------------------------------------------------------------------

/**
 * Builds an [ItunesLibrary] backed by [count] virtual audio files in the `/corpus/itunes/`
 * directory on [fileSystem]. Metadata is registered with [metadataIO] so the library can
 * resolve each file via `audioItemFromFile`.
 */
private fun buildItunesCorpus(
    count: Int,
    fileSystem: FileSystem,
    metadataIO: VolatileAudioMetadataIO
): ItunesLibrary {
    val tracks = mutableMapOf<Int, ItunesTrack>()
    for (i in 1..count) {
        val path = fileSystem.getPath("/corpus/itunes/itunes-track-${i.toString().padStart(6, '0')}.mp3")
        Files.createDirectories(path.parent)
        val metadata = AudioItemMetadata(title = "iTunes Track $i")
        metadataIO.createVirtualFile(path, metadata)
        val track =
            ItunesTrack(
                id = i,
                title = "iTunes Track $i",
                artist = "Artist ${i % 50}",
                albumArtist = "Artist ${i % 50}",
                album = "Album ${i % 200}",
                genre = "Electronic",
                year = 2024.toShort(),
                trackNumber = (i % 20 + 1).toShort(),
                discNumber = 1.toShort(),
                totalTimeMs = 180_000L,
                bitRate = 320,
                playCount = 0,
                rating = 0,
                bpm = null,
                comments = null,
                location = path.toUri().toString(),
                isCompilation = false,
                persistentId = "STRESS-$i",
                dateAdded = null
            )
        tracks[i] = track
    }
    val playlist =
        ItunesPlaylist(
            name = "Stress iTunes",
            persistentId = "STRESS-PL",
            parentPersistentId = null,
            isFolder = false,
            trackIds = tracks.keys.toList()
        )
    return ItunesLibrary(tracks, listOf(playlist))
}

/**
 * Builds an M3U file at `/corpus/m3u/stress.m3u` on [fileSystem] referencing [count] virtual
 * audio files under `/corpus/m3u/tracks/`. Metadata is registered with [metadataIO].
 *
 * @return the path to the generated M3U playlist file
 */
private fun buildM3uCorpus(
    count: Int,
    fileSystem: FileSystem,
    metadataIO: VolatileAudioMetadataIO
): java.nio.file.Path {
    val tracksDir = fileSystem.getPath("/corpus/m3u/tracks")
    Files.createDirectories(tracksDir)

    val trackPaths = mutableListOf<String>()
    for (i in 1..count) {
        val path = tracksDir.resolve("m3u-track-${i.toString().padStart(6, '0')}.mp3")
        val metadata = AudioItemMetadata(title = "M3U Track $i")
        metadataIO.createVirtualFile(path, metadata)
        trackPaths.add(path.toAbsolutePath().toString())
    }

    val m3uDir = fileSystem.getPath("/corpus/m3u")
    val m3uPath = m3uDir.resolve("stress.m3u")
    val content =
        buildString {
            appendLine("#EXTM3U")
            trackPaths.forEach { p -> appendLine(p) }
        }
    Files.write(m3uPath, content.toByteArray(Charsets.UTF_8))
    return m3uPath
}