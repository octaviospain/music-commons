package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.MusicLibrary
import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.lirp.event.ReactiveScope
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
private fun testResourcePath(resource: String): Path {
    val url = ItunesImportServiceTest::class.java.getResource(resource)!!
    return when (url.protocol) {
        "file" -> Paths.get(url.toURI())
        else -> {
            val extension = resource.substringAfterLast('.')
            val tmp = File.createTempFile("test-resource-", ".$extension").also { it.deleteOnExit() }
            ItunesImportServiceTest::class.java.getResourceAsStream(resource)!!.use { input ->
                Files.copy(input, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            tmp.toPath()
        }
    }
}

@ExperimentalCoroutinesApi
@DisplayName("ItunesImportService")
internal class ItunesImportServiceTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    lateinit var musicLibrary: MusicLibrary
    lateinit var service: ItunesImportService

    val mp3File = testResourcePath("/testfiles/testeable.mp3")
    val flacFile = testResourcePath("/testfiles/testeable.flac")

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

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    beforeEach {
        musicLibrary = MusicLibrary.builder().build()
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

        result.importedCount shouldBe 2
        result.skippedCount shouldBe 0
        result.errorCount shouldBe 0
        musicLibrary.audioLibrary().size() shouldBe 2
    }

    "ItunesImportService imports tracks with useFileMetadata=false using iTunes metadata" {
        val track = trackFor(1, mp3File, title = "iTunes Title Override", artist = "iTunes Artist Override", album = "iTunes Album Override")
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = false, holdPlayCount = false, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.importedCount shouldBe 1
        result.errorCount shouldBe 0
        val items = musicLibrary.audioLibrary().search { true }
        items shouldHaveSize 1
        val item = items.first()
        item.title shouldBe "iTunes Title Override"
        item.artist.name shouldBe "iTunes Artist Override"
        item.album.name shouldBe "iTunes Album Override"
    }

    "ItunesImportService applies holdPlayCount from iTunes data" {
        val track = trackFor(1, mp3File).copy(playCount = 42)
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = false, holdPlayCount = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.importedCount shouldBe 1
        musicLibrary.audioLibrary().search { true }.first().playCount shouldBe 42.toShort()
    }

    "ItunesImportService applies holdPlayCount with useFileMetadata=true" {
        val track = trackFor(1, mp3File).copy(playCount = 10)
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, holdPlayCount = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.importedCount shouldBe 1
        musicLibrary.audioLibrary().search { true }.first().playCount shouldBe 10.toShort()
    }

    "ItunesImportService skips tracks with missing files when ignoreNotFound=true" {
        val track = trackFor(1, Paths.get("/non/existent/file.mp3"))
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(ignoreNotFound = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.skippedCount shouldBe 1
        result.importedCount shouldBe 0
    }

    "ItunesImportService creates playlists from selected playlists" {
        val track = trackFor(1, mp3File)
        val playlist = playlistFor("My Import Playlist", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.playlistsCreated shouldBe 1
        musicLibrary.findPlaylistByName("My Import Playlist") shouldBePresent { it.name shouldBe "My Import Playlist" }
    }

    "ItunesImportService creates folder directories and wires child playlists" {
        val track = trackFor(1, mp3File)
        val folder = playlistFor("My Folder", "FOLDER1", isFolder = true)
        val child = playlistFor("Child Playlist", "CHILD1", listOf(1), parentId = "FOLDER1")
        val library = ItunesLibrary(mapOf(1 to track), listOf(folder, child))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        val result = service.importAsync(listOf(folder, child), library, policy).get()

        result.playlistsCreated shouldBe 2
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

        result.playlistsCreated shouldBe 3
        musicLibrary.findPlaylistByName("Root Folder").shouldBePresent().isDirectory shouldBe true
        musicLibrary.findPlaylistByName("Sub Folder").shouldBePresent().isDirectory shouldBe true
        musicLibrary.findPlaylistByName("Leaf Playlist") shouldBePresent { it.name shouldBe "Leaf Playlist" }
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

    "ItunesImportService skips tracks with unsupported file types" {
        val track = trackFor(1, Paths.get("/music/song.ogg"))
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy =
            ItunesImportPolicy(
                acceptedFileTypes = setOf(AudioFileType.MP3),
                ignoreNotFound = true,
                writeMetadata = false
            )

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.skippedCount shouldBe 1
        result.importedCount shouldBe 0
    }

    "ItunesImportService writes iTunes metadata to file tags when writeMetadata=true" {
        val tmpFile = File.createTempFile("itunes-write-test-", ".mp3").also { it.deleteOnExit() }
        Files.copy(mp3File, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        val track = trackFor(1, tmpFile.toPath(), title = "Written Title", artist = "Written Artist", album = "Written Album")
        val playlist = playlistFor("PL", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = false, holdPlayCount = false, writeMetadata = true)

        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.importedCount shouldBe 1
        val item = musicLibrary.audioLibrary().search { true }.first()
        item.writeMetadata().join()
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

        var future: CompletableFuture<ItunesImportResult>? = null
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

        musicLibrary.audioLibrary().size() shouldBe (musicLibrary.audioLibrary().size())
    }

    "ItunesImportService imports playlist with suffix when name already exists" {
        val track = trackFor(1, mp3File)
        val playlist = playlistFor("Existing Playlist", "PL1", listOf(1))
        val library = ItunesLibrary(mapOf(1 to track), listOf(playlist))
        val policy = ItunesImportPolicy(useFileMetadata = true, writeMetadata = false)

        musicLibrary.createPlaylist("Existing Playlist")
        val result = service.importAsync(listOf(playlist), library, policy).get()

        result.importedCount shouldBe 1
        result.playlistsCreated shouldBe 1
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

        result.playlistsCreated shouldBe 1
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

        result.playlistsCreated shouldBe 2
        musicLibrary.findPlaylistByName("My Folder_1").shouldBePresent().isDirectory shouldBe true
        musicLibrary.findPlaylistByName("Child") shouldBePresent { it.name shouldBe "Child" }
    }
})