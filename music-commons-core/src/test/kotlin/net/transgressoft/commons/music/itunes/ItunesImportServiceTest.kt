package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.ArbitraryAudioFile
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.util.OsDetector
import io.kotest.core.annotation.DisplayName
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@DisplayName("ItunesImportService")
@Isolate
internal class ItunesImportServiceTest : StringSpec({

    val reactive = reactiveScope()

    lateinit var musicLibrary: CoreMusicLibrary
    lateinit var service: ItunesImportService<AudioItem, MutableAudioPlaylist>

    val mp3File = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable.mp3").toPath()
    val flacFile = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable.flac").toPath()

    fun trackFor(
        id: Int,
        path: Path,
        title: String = "iTunes Title",
        artist: String = "iTunes Artist",
        album: String = "iTunes Album"
    ): ItunesTrack =
        ItunesTrack(
            id = id,
            title = title,
            artist = artist,
            albumArtist = artist,
            album = album,
            genre = "Rock",
            year = 2020,
            trackNumber = 1,
            discNumber = 1,
            totalTimeMs = 180000L,
            bitRate = 320,
            playCount = 5,
            rating = 80,
            bpm = 120f,
            comments = "Test comment",
            location = path.toUri().toString(),
            isCompilation = false,
            persistentId = "TRACK-$id",
            dateAdded = LocalDateTime.of(2020, 1, 1, 0, 0)
        )

    fun playlistFor(
        name: String,
        persistentId: String,
        trackIds: List<Int> = emptyList(),
        isFolder: Boolean = false,
        parentId: String? = null
    ): ItunesPlaylist =
        ItunesPlaylist(name = name, persistentId = persistentId, parentPersistentId = parentId, isFolder = isFolder, trackIds = trackIds)

    beforeEach {
        musicLibrary = CoreMusicLibrary.builder().build()
        service = ItunesImportService(musicLibrary)
    }

    afterEach {
        musicLibrary.close()
    }

    "ItunesImportService imports tracks with useFileMetadata=true using createFromFile" {
        val track1 = trackFor(1, mp3File)
        val track2 = trackFor(2, flacFile)
        val playlist = playlistFor("My Playlist", "PL1", listOf(1, 2))
        val library = ItunesLibrary(mapOf(1 to track1, 2 to track2), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, holdPlayCount = false, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.imported.size shouldBe 2
        result.unresolved shouldHaveSize 0
        musicLibrary.audioLibrary().size() shouldBe 2
    }

    "ItunesImportService imports tracks with useFileMetadata=false using iTunes metadata" {
        val track = trackFor(1, mp3File, title = "iTunes Title Override", artist = "iTunes Artist Override", album = "iTunes Album Override")
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = false, holdPlayCount = false, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.imported.size shouldBe 1
        result.unresolved shouldHaveSize 0
        val items = musicLibrary.audioLibrary().search { true }
        items shouldHaveSize 1
        val item = items.first()
        item.title shouldBe "iTunes Title Override"
        item.artist.name shouldBe "iTunes Artist Override"
        item.album.name shouldBe "iTunes Album Override"
        musicLibrary
            .audioLibrary()
            .getArtistCatalog(Artist.of("iTunes Artist Override"))
            .shouldBePresent { catalog ->
                catalog.albums.map { it.albumName } shouldContain "iTunes Album Override"
            }
    }

    "ItunesImportService applies holdPlayCount from iTunes data" {
        val track = trackFor(1, mp3File).copy(playCount = 42)
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = false, holdPlayCount = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.imported.size shouldBe 1
        musicLibrary.audioLibrary().search { true }.first().playCount shouldBe 42.toShort()
    }

    "ItunesImportService applies holdPlayCount with useFileMetadata=true" {
        val track = trackFor(1, mp3File).copy(playCount = 10)
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, holdPlayCount = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.imported.size shouldBe 1
        musicLibrary.audioLibrary().search { true }.first().playCount shouldBe 10.toShort()
    }

    "ItunesImportService reports missing files in unresolved bucket with FileNotFound reason" {
        val track = trackFor(1, Paths.get("/non/existent/file.mp3"))
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.unresolved shouldHaveSize 1
        result.unresolved.first().reason shouldBe UnresolvedReason.FileNotFound
        result.imported shouldHaveSize 0
    }

    "ItunesImportService creates playlists from selected playlists" {
        val track = trackFor(1, mp3File)
        val playlist = playlistFor("My Import Playlist", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.rejectedPlaylistNames shouldHaveSize 0
        musicLibrary.findPlaylistByName("My Import Playlist") shouldBePresent { it.name shouldBe "My Import Playlist" }
    }

    "ItunesImportService creates folder directories and wires child playlists" {
        val track = trackFor(1, mp3File)
        val folder = playlistFor("My Folder", "FOLDER1", isFolder = true)
        val child = playlistFor("Child Playlist", "CHILD1", listOf(1), parentId = "FOLDER1")
        val library = ItunesLibrary(mapOf(1 to track), listOf(folder, child))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(folder, child), library, policy).get()

        result.rejectedPlaylistNames shouldHaveSize 0
        musicLibrary.findPlaylistByName("My Folder").shouldBePresent().isDirectory shouldBe true
        musicLibrary.findPlaylistByName("Child Playlist") shouldBePresent { it.name shouldBe "Child Playlist" }
    }

    "ItunesImportService wires nested folder playlists recursively" {
        val track = trackFor(1, mp3File)
        val grandparent = playlistFor("Root Folder", "GF1", isFolder = true)
        val parent = playlistFor("Sub Folder", "PF1", isFolder = true, parentId = "GF1")
        val child = playlistFor("Leaf Playlist", "CHILD1", listOf(1), parentId = "PF1")
        val library = ItunesLibrary(mapOf(1 to track), listOf(grandparent, parent, child))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(grandparent, parent, child), library, policy).get()

        result.rejectedPlaylistNames shouldHaveSize 0
        musicLibrary.findPlaylistByName("Root Folder").shouldBePresent().isDirectory shouldBe true
        musicLibrary.findPlaylistByName("Sub Folder").shouldBePresent().isDirectory shouldBe true
        musicLibrary.findPlaylistByName("Leaf Playlist") shouldBePresent { it.name shouldBe "Leaf Playlist" }
    }

    "ItunesImportService preserves ancestor folders when only leaf playlists are selected" {
        val track = trackFor(1, mp3File)
        val grandparent = playlistFor("Root Folder", "GF1", isFolder = true)
        val parent = playlistFor("Sub Folder", "PF1", isFolder = true, parentId = "GF1")
        val child = playlistFor("Leaf Playlist", "CHILD1", listOf(1), parentId = "PF1")
        val library = ItunesLibrary(mapOf(1 to track), listOf(grandparent, parent, child))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(child), library, policy).get()

        result.rejectedPlaylistNames shouldHaveSize 0
        musicLibrary.findPlaylistByName("Root Folder").shouldBePresent().isDirectory shouldBe true
        musicLibrary.findPlaylistByName("Sub Folder").shouldBePresent().isDirectory shouldBe true
        musicLibrary.findPlaylistByName("Leaf Playlist") shouldBePresent { it.name shouldBe "Leaf Playlist" }
    }

    "ItunesImportService skips ancestor lookup when ancestor is missing from the library" {
        val track = trackFor(1, mp3File)
        val orphanLeaf = playlistFor("Orphan Leaf", "ORPHAN1", listOf(1), parentId = "MISSING_FOLDER")
        val library = ItunesLibrary(mapOf(1 to track), listOf(orphanLeaf))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(orphanLeaf), library, policy).get()

        result.rejectedPlaylistNames shouldHaveSize 0
        musicLibrary.findPlaylistByName("Orphan Leaf") shouldBePresent { it.name shouldBe "Orphan Leaf" }
    }

    "ItunesImportService wires top-level imported playlists to the supplied rootDirectoryName" {
        val track = trackFor(1, mp3File)
        val track2 = trackFor(2, flacFile, title = "Other")
        val rootContainer = musicLibrary.playlistHierarchy().createPlaylistDirectory("My Root")
        val topLevelLeaf = playlistFor("Solo Leaf", "SOLO1", listOf(1))
        val topLevelFolder = playlistFor("Top Folder", "TOP1", isFolder = true)
        val nestedLeaf = playlistFor("Nested Leaf", "NEST1", listOf(2), parentId = "TOP1")
        val library = ItunesLibrary(mapOf(1 to track, 2 to track2), listOf(topLevelLeaf, topLevelFolder, nestedLeaf))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        service.importAsync(listOf(topLevelLeaf, topLevelFolder, nestedLeaf), library, policy, rootDirectoryName = "My Root").get()

        // Top-level imported playlists land inside My Root; the nested leaf stays inside its iTunes parent folder.
        val rootChildren = rootContainer.playlists.map { it.name }
        rootChildren shouldContain "Solo Leaf"
        rootChildren shouldContain "Top Folder"
        val topFolder = musicLibrary.findPlaylistByName("Top Folder").get()
        topFolder.playlists.map { it.name } shouldContain "Nested Leaf"
    }

    "ItunesImportService leaves top-level imported playlists orphaned when rootDirectoryName is null" {
        val track = trackFor(1, mp3File)
        val rootContainer = musicLibrary.playlistHierarchy().createPlaylistDirectory("My Root")
        val topLevelLeaf = playlistFor("Solo Leaf", "SOLO1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(topLevelLeaf))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        service.importAsync(listOf(topLevelLeaf), library, policy).get()

        rootContainer.playlists.map { it.name } shouldNotContain "Solo Leaf"
        musicLibrary.findPlaylistByName("Solo Leaf").isPresent shouldBe true
    }

    "ItunesImportService gracefully skips top-level wiring when rootDirectoryName does not exist in the library" {
        val track = trackFor(1, mp3File)
        val topLevelLeaf = playlistFor("Solo Leaf", "SOLO1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(topLevelLeaf))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        service.importAsync(listOf(topLevelLeaf), library, policy, rootDirectoryName = "NotInLibrary").get()

        musicLibrary.findPlaylistByName("Solo Leaf").isPresent shouldBe true
    }

    "ItunesImportService reports progress via callback" {
        val track1 = trackFor(1, mp3File)
        val track2 = trackFor(2, flacFile)
        val playlist = playlistFor("PL", "PL1", listOf(1, 2))
        val library = ItunesLibrary(mapOf(1 to track1, 2 to track2), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val progressSnapshots = mutableListOf<ImportProgress>()
        service.importAsync(listOf(playlist), library, policy) { progress ->
            progressSnapshots.add(progress)
        }.get()

        progressSnapshots shouldHaveSize 2
        progressSnapshots.last().itemsProcessed shouldBe 2
        progressSnapshots.last().totalItems shouldBe 2
    }

    "ItunesImportService reports unsupported file types in unresolved bucket" {
        val track = trackFor(1, Paths.get("/music/song.ogg"))
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy =
            ItunesImportPolicy(
                acceptedFileTypes = setOf(AudioFileType.MP3),
                writeMetadata = false
            )

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.unresolved shouldHaveSize 1
        result.unresolved.first().reason shouldBe UnresolvedReason.UnsupportedType("ogg")
        result.imported shouldHaveSize 0
    }

    "ItunesImportService writes iTunes metadata to file tags when writeMetadata=true" {
        val tmpFile = File.createTempFile("itunes-write-test-", ".mp3").also { it.deleteOnExit() }
        Files.copy(mp3File, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        val track = trackFor(1, tmpFile.toPath(), title = "Written Title", artist = "Written Artist", album = "Written Album")
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = false, holdPlayCount = false, writeMetadata = true)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.imported.size shouldBe 1
        val audioFile = AudioFileIO.read(tmpFile)
        audioFile.tag.getFirst(FieldKey.TITLE) shouldBe "Written Title"
        audioFile.tag.getFirst(FieldKey.ARTIST) shouldBe "Written Artist"
        audioFile.tag.getFirst(FieldKey.ALBUM) shouldBe "Written Album"
    }

    "ItunesImportService cancellation stops import via CompletableFuture cancel" {
        val tracks = (1..20).associateWith { i -> trackFor(i, mp3File, title = "Track $i") }
        val playlist = playlistFor("PL", "PL1", (1..20).toList())
        val library = ItunesLibrary(tracks, listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        var future: CompletableFuture<ImportResult>? = null
        future =
            service.importAsync(listOf(playlist), library, policy) { progress ->
                if (progress.itemsProcessed >= 1) {
                    future?.cancel(true)
                }
            }

        try {
            future.get()
        } catch (_: CancellationException) {
            // Expected — future was cancelled
        } catch (e: java.util.concurrent.ExecutionException) {
            (e.cause is CancellationException || e.cause is kotlinx.coroutines.CancellationException) shouldBe true
        }

        // Cancellation is proven by the exception above; the library should have fewer than all 20 tracks
        musicLibrary.audioLibrary().size() shouldBeLessThan 20
    }

    "ItunesImportService imports playlist with suffix when name already exists" {
        val track = trackFor(1, mp3File)
        val playlist = playlistFor("Existing Playlist", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        musicLibrary.createPlaylist("Existing Playlist")
        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.imported.size shouldBe 1
        result.rejectedPlaylistNames shouldHaveSize 0
        musicLibrary.findPlaylistByName("Existing Playlist_1") shouldBePresent { it.name shouldBe "Existing Playlist_1" }
    }

    "ItunesImportService increments suffix when multiple collisions exist" {
        val track = trackFor(1, mp3File)
        val playlist = playlistFor("Duped", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        musicLibrary.createPlaylist("Duped")
        musicLibrary.createPlaylist("Duped_1")
        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.rejectedPlaylistNames shouldHaveSize 0
        musicLibrary.findPlaylistByName("Duped_2") shouldBePresent { it.name shouldBe "Duped_2" }
    }

    "ItunesImportService imports folder directory with suffix when name already exists" {
        val track = trackFor(1, mp3File)
        val folder = playlistFor("My Folder", "FOLDER1", isFolder = true)
        val child = playlistFor("Child", "CHILD1", listOf(1), parentId = "FOLDER1")
        val library = ItunesLibrary(mapOf(1 to track), listOf(folder, child))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        musicLibrary.createPlaylistDirectory("My Folder")
        val result = service.importAsync(listOf(folder, child), library, policy).get()

        result.rejectedPlaylistNames shouldHaveSize 0
        musicLibrary.findPlaylistByName("My Folder_1").shouldBePresent().isDirectory shouldBe true
        musicLibrary.findPlaylistByName("Child") shouldBePresent { it.name shouldBe "Child" }
    }

    "ItunesImportService imports baseline with all tracks and one playlist" {
        val track1 = trackFor(1, mp3File, title = "Song One")
        val track2 = trackFor(2, flacFile, title = "Song Two")
        val playlist = playlistFor("Baseline Playlist", "BPL001", listOf(1, 2))
        val library = ItunesLibrary(mapOf(1 to track1, 2 to track2), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.imported shouldHaveSize 2
        result.unresolved.shouldBeEmpty()
        result.rejectedPlaylistNames.shouldBeEmpty()
    }

    "ItunesImportService resolves NFD-encoded filenames via NFC normalization" {
        // Explicit \u escapes so editor/IDE auto-normalization cannot collapse the two forms.
        // NFC: precomposed e-acute (U+00E9). NFD: e (U+0065) + combining acute (U+0301).
        val nfcName = "caf\u00e9.mp3"
        val nfdName = "cafe\u0301.mp3"
        check(nfdName != nfcName) { "NFD and NFC test inputs collapsed to the same string" }
        val tmpDir = Files.createTempDirectory("itunes-nfc-test")
        val nfcFile = tmpDir.resolve(nfcName)
        Files.copy(mp3File, nfcFile, StandardCopyOption.REPLACE_EXISTING)

        // Build a track whose location uses NFD form (e + combining acute)
        val nfdLocation = nfcFile.parent.resolve(nfdName).toUri().toString()
        val track =
            ItunesTrack(
                id = 1,
                title = "Café Song",
                artist = "Artist",
                albumArtist = "Artist",
                album = "Album",
                genre = null,
                year = null,
                trackNumber = null,
                discNumber = null,
                totalTimeMs = 180000L,
                bitRate = 320,
                playCount = 0,
                rating = 0,
                bpm = null,
                comments = null,
                location = nfdLocation,
                isCompilation = false,
                persistentId = "NFD-1",
                dateAdded = LocalDateTime.of(2020, 1, 1, 0, 0)
            )
        val playlist = playlistFor("NFD Playlist", "NPL001", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        try {
            result.imported shouldHaveSize 1
            result.unresolved.shouldBeEmpty()
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    "ItunesImportService rejects Windows-forbidden playlist names when isWindows=true" {
        val track = trackFor(1, mp3File, title = "Valid Song")
        val forbidden = playlistFor("My|Playlist", "WPL001", listOf(1))
        val reserved = playlistFor("NUL", "WPL002", listOf(1))
        val trailingDot = playlistFor("valid.", "WPL003", listOf(1))
        val allPlaylists = listOf(forbidden, reserved, trailingDot)
        val library = ItunesLibrary(mapOf(1 to track), allPlaylists)
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        OsDetector.withOverriddenIsWindows(true) {
            val result = service.importAsync(allPlaylists, library, policy).get()

            result.imported shouldHaveSize 1
            result.rejectedPlaylistNames shouldHaveSize 3
            result.rejectedPlaylistNames[0].name shouldBe "My|Playlist"
            result.rejectedPlaylistNames[0].reason.shouldBeInstanceOf<RejectionReason.ForbiddenChar>()
            (result.rejectedPlaylistNames[0].reason as RejectionReason.ForbiddenChar).char shouldBe '|'
            result.rejectedPlaylistNames[1].name shouldBe "NUL"
            result.rejectedPlaylistNames[1].reason shouldBe RejectionReason.ReservedName
            result.rejectedPlaylistNames[2].name shouldBe "valid."
            result.rejectedPlaylistNames[2].reason shouldBe RejectionReason.TrailingDotOrSpace
        }
    }

    "ItunesImportService accepts Windows-forbidden playlist names on Linux (pass-through)" {
        val track = trackFor(1, mp3File, title = "Valid Song")
        val forbidden = playlistFor("My|Playlist", "WPL001", listOf(1))
        val reserved = playlistFor("NUL", "WPL002", listOf(1))
        val trailingDot = playlistFor("valid.", "WPL003", listOf(1))
        val allPlaylists = listOf(forbidden, reserved, trailingDot)
        val library = ItunesLibrary(mapOf(1 to track), allPlaylists)
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        OsDetector.withOverriddenIsWindows(false) {
            val result = service.importAsync(allPlaylists, library, policy).get()

            result.imported shouldHaveSize 1
            result.rejectedPlaylistNames.shouldBeEmpty()
        }
    }

    "ItunesImportService resolveTrackPath is idempotent for ASCII input" {
        val track = trackFor(1, mp3File, title = "ASCII Song")
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.imported shouldHaveSize 1
        result.unresolved.shouldBeEmpty()
    }

    "ItunesImportService imports every library track when a subset of playlists is selected" {
        val track1 = trackFor(1, mp3File, title = "Song One")
        val track2 = trackFor(2, flacFile, title = "Song Two")
        val track3 = trackFor(3, mp3File, title = "Song Three")
        val playlistA = playlistFor("Playlist A", "PLA", listOf(1))
        val playlistB = playlistFor("Playlist B", "PLB", listOf(2))
        val library = ItunesLibrary(mapOf(1 to track1, 2 to track2, 3 to track3), listOf(playlistA, playlistB))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlistA), library, policy).get()

        result.imported shouldHaveSize 3
        result.unresolved.shouldBeEmpty()
        musicLibrary.audioLibrary().size() shouldBe 3
    }

    "ItunesImportService imports every library track when no playlists are selected" {
        val track1 = trackFor(1, mp3File, title = "Song One")
        val track2 = trackFor(2, flacFile, title = "Song Two")
        val track3 = trackFor(3, mp3File, title = "Song Three")
        val playlistA = playlistFor("Playlist A", "PLA", listOf(1))
        val playlistB = playlistFor("Playlist B", "PLB", listOf(2))
        val library = ItunesLibrary(mapOf(1 to track1, 2 to track2, 3 to track3), listOf(playlistA, playlistB))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(emptyList(), library, policy).get()

        result.imported shouldHaveSize 3
        result.rejectedPlaylistNames.shouldBeEmpty()
        musicLibrary.findPlaylistByName("Playlist A").isPresent shouldBe false
        musicLibrary.findPlaylistByName("Playlist B").isPresent shouldBe false
    }

    "ItunesImportService recreates only selected playlists and ancestor folders while importing all tracks" {
        val track1 = trackFor(1, mp3File, title = "Song One")
        val track2 = trackFor(2, flacFile, title = "Song Two")
        val track3 = trackFor(3, mp3File, title = "Song Three")
        val folder = playlistFor("Music", "F1", isFolder = true)
        val playlistA = playlistFor("Playlist A", "PLA", listOf(1), parentId = "F1")
        val playlistB = playlistFor("Playlist B", "PLB", listOf(2))
        val library = ItunesLibrary(mapOf(1 to track1, 2 to track2, 3 to track3), listOf(folder, playlistA, playlistB))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlistA), library, policy).get()

        result.imported shouldHaveSize 3
        musicLibrary.findPlaylistByName("Music").isPresent shouldBe true
        musicLibrary.findPlaylistByName("Playlist A").isPresent shouldBe true
        musicLibrary.findPlaylistByName("Playlist B").isPresent shouldBe false
    }

    "ItunesImportService reports totalItems equal to the iTunes library track count" {
        val track1 = trackFor(1, mp3File, title = "Song One")
        val track2 = trackFor(2, flacFile, title = "Song Two")
        val track3 = trackFor(3, mp3File, title = "Song Three")
        val track4 = trackFor(4, flacFile, title = "Song Four")
        val playlist = playlistFor("Subset Playlist", "SUB1", listOf(1, 2))
        val library = ItunesLibrary(mapOf(1 to track1, 2 to track2, 3 to track3, 4 to track4), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val progresses = mutableListOf<ImportProgress>()
        service.importAsync(listOf(playlist), library, policy) { progresses.add(it) }.get()

        progresses.shouldHaveSize(4)
        progresses.forEach { it.totalItems shouldBe library.tracks.size }
        progresses.last().itemsProcessed shouldBe 4
    }

    "ItunesImportService surfaces iCloud-style tracks with no local file as UnresolvedTrack(FileNotFound)" {
        val localTrack = trackFor(1, mp3File, title = "Local Song")
        val iCloudPath = Paths.get("/icloud/missing/streaming-only.mp3")
        val iCloudTrack = trackFor(2, iCloudPath, title = "iCloud Only Song")
        val library = ItunesLibrary(mapOf(1 to localTrack, 2 to iCloudTrack), emptyList())
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(emptyList(), library, policy).get()

        result.imported shouldHaveSize 1
        result.unresolved shouldHaveSize 1
        result.unresolved.first().reason shouldBe UnresolvedReason.FileNotFound
        result.unresolved.first().title shouldBe "iCloud Only Song"
    }
})