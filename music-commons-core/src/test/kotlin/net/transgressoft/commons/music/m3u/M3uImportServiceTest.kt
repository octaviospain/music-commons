package net.transgressoft.commons.music.m3u

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.m3u.M3uTestFixtures.PlaylistFixture
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.testing.reactiveScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.FileSystem
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@DisplayName("M3uImportService")
internal class M3uImportServiceTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()
    val fs: FileSystem = files.fileSystem

    lateinit var library: CoreMusicLibrary
    lateinit var service: M3uImportService

    beforeEach {
        library = CoreMusicLibrary.builder().build()
        service = M3uImportService(library)
    }

    afterEach {
        service.close()
        library.close()
    }

    fun preseed(tracks: List<M3uTestFixtures.TrackInfo>) {
        tracks.forEach { trackInfo ->
            val spyPath = M3uTestFixtures.materializeTrack(trackInfo, files)
            library.audioItemFromFile(spyPath)
        }
    }

    fun importFromResource(resource: String): Pair<PlaylistFixture, MutableAudioPlaylist> {
        val fixture = M3uTestFixtures.loadPlaylist(resource, files)
        preseed(fixture.tracks)
        return fixture to service.import(fixture.rootPath)
    }

    "imports flat playlist from static Kobayashi resource and links library tracks" {
        val (fixture, playlist) = importFromResource("Kobayashi.m3u")

        playlist.name shouldBe "Kobayashi"
        playlist.audioItems shouldHaveSize fixture.tracks.size

        val expectedTitles = fixture.tracks.map { it.title }
        val expectedArtists = fixture.tracks.map { it.artist }
        playlist.audioItems.map { it.title } shouldBe expectedTitles
        playlist.audioItems.map { it.artist.name } shouldBe expectedArtists
        playlist.audioItems.map { it.album.name }.toSet() shouldContainAll setOf("20 Years of Techno on Planet Kobayashi")
    }

    "imports nested Raver hierarchy preserving directory structure and child playlists" {
        val (fixture, parent) = importFromResource("Raver.m3u")

        parent.name shouldBe "Raver"
        parent.isDirectory.shouldBeTrue()
        parent.playlists.map { it.name } shouldContainExactlyInAnyOrder listOf("Core", "CoreTunel")

        val recursiveItems = parent.audioItemsRecursive
        recursiveItems shouldHaveSize fixture.tracks.size
        recursiveItems.map { it.title } shouldContainAll fixture.tracks.map { it.title }
    }

    "rejects duplicate playlist name with M3uImportException without leaving partial state" {
        val (firstFixture, _) = importFromResource("Kobayashi.m3u")
        val sizeAfterFirstImport = library.audioLibrary().size()

        // Re-import the same root from the same resource — same derived name "Kobayashi".
        val duplicateRoot =
            M3uTestFixtures.loadPlaylist(
                "Kobayashi.m3u",
                virtualFiles = files,
                playlistsDir = "/music/Playlists2"
            ).rootPath

        val ex = shouldThrow<M3uImportException> { service.import(duplicateRoot) }
        ex.message!! shouldContain "already exists"

        // Pre-collision detection must NOT have created any additional playlists.
        library.audioLibrary().size() shouldBe sizeAfterFirstImport
        firstFixture.tracks // referenced to keep linter happy in case of future churn
    }

    "rejects nested playlist name collision up-front without leaking the deep child or its siblings" {
        // First import populates the library with "Raver", "Core", "CoreTunel".
        importFromResource("Raver.m3u")
        val before = library.audioLibrary().size()
        val playlistCountBefore = library.playlistHierarchy().numberOfPlaylists()

        // Construct a second tree whose root name is unique but contains a child colliding with "Core".
        val secondRoot = fs.getPath("/music/Playlists/Other.m3u")
        val secondDir = secondRoot.parent.resolve("Other")
        Files.createDirectories(secondDir)
        Files.writeString(secondRoot, "#EXTM3U\nOther/Unique.m3u\nOther/Core.m3u\n")
        Files.writeString(secondDir.resolve("Unique.m3u"), "#EXTM3U\n")
        Files.writeString(secondDir.resolve("Core.m3u"), "#EXTM3U\n")

        val ex = shouldThrow<M3uImportException> { service.import(secondRoot) }
        ex.message!! shouldContain "Core"

        // None of "Other", "Unique" or the colliding "Core" should have been materialized.
        library.findPlaylistByName("Other").isPresent shouldBe false
        library.findPlaylistByName("Unique").isPresent shouldBe false
        library.audioLibrary().size() shouldBe before
        library.playlistHierarchy().numberOfPlaylists() shouldBe playlistCountBefore
    }

    "rejects duplicate playlist names within the import tree itself" {
        // Two nested children whose filenames yield the same derived playlist name "Mix".
        val baseDir = fs.getPath("/music/Playlists")
        Files.createDirectories(baseDir.resolve("a"))
        Files.createDirectories(baseDir.resolve("b"))
        Files.writeString(baseDir.resolve("a/Mix.m3u"), "#EXTM3U\n")
        Files.writeString(baseDir.resolve("b/Mix.m3u"), "#EXTM3U\n")
        val root = baseDir.resolve("Root.m3u")
        Files.writeString(root, "#EXTM3U\na/Mix.m3u\nb/Mix.m3u\n")

        val ex = shouldThrow<M3uImportException> { service.import(root) }
        ex.message!! shouldContain "duplicate playlist name 'Mix'"

        // Nothing in the conflicting tree should have been materialized.
        library.findPlaylistByName("Root").isPresent shouldBe false
        library.findPlaylistByName("Mix").isPresent shouldBe false
    }

    "throws M3uCycleException on direct self-reference" {
        val selfPath = fs.getPath("/music/Playlists/self.m3u")
        Files.createDirectories(selfPath.parent)
        Files.writeString(selfPath, "#EXTM3U\nself.m3u\n")

        val ex = shouldThrow<M3uCycleException> { service.import(selfPath) }
        ex.message!! shouldContain "Cycle detected:"
        ex.message!! shouldContain selfPath.toRealPath().toString()
    }

    "throws M3uCycleException on mutual A to B to A cycle" {
        val baseDir = fs.getPath("/music/Playlists")
        Files.createDirectories(baseDir)
        val a = baseDir.resolve("a.m3u")
        val b = baseDir.resolve("b.m3u")
        Files.writeString(a, "#EXTM3U\nb.m3u\n")
        Files.writeString(b, "#EXTM3U\na.m3u\n")

        val ex = shouldThrow<M3uCycleException> { service.import(a) }
        ex.message!! shouldContain "Cycle detected:"
        ex.message!! shouldContain "a.m3u"
        ex.message!! shouldContain "b.m3u"
    }

    "throws M3uImportException when nested depth exceeds maxDepth" {
        val depthLimited = M3uImportService(library, maxDepth = 0)
        depthLimited.use { depthLimited ->
            val baseDir = fs.getPath("/music/Playlists")
            Files.createDirectories(baseDir)
            val parent = baseDir.resolve("parent.m3u")
            val child = baseDir.resolve("child.m3u")
            Files.writeString(parent, "#EXTM3U\nchild.m3u\n")
            Files.writeString(child, "#EXTM3U\n")

            val ex = shouldThrow<M3uImportException> { depthLimited.import(parent) }
            ex.message!! shouldContain "exceeded maximum depth 0"
        }
    }

    "translates missing root path failure to M3uParseException" {
        val missing = fs.getPath("/music/Playlists/missing.m3u")
        Files.createDirectories(missing.parent)

        val ex = shouldThrow<M3uParseException> { service.import(missing) }
        ex.message!! shouldContain "missing.m3u"
    }

    "skips missing nested playlist references and continues importing siblings" {
        val (fixture, _) = importFromResource("Simple.m3u")
        val rootDir = fixture.rootPath.parent

        // Append a missing nested reference to the playlist content and re-import as a separate root.
        val secondRoot = rootDir.resolve("WithMissingChild.m3u")
        Files.writeString(
            secondRoot,
            """
            #EXTM3U
            ../Library/NeoKaoss/Naciones Hundidas/12 WeAreMutties.flac
            missing-child.m3u
            """.trimIndent()
        )

        val playlist = service.import(secondRoot)
        playlist.audioItems shouldHaveSize 1
        playlist.playlists shouldHaveSize 0
    }

    "skips entries with unsupported file extensions" {
        val baseDir = fs.getPath("/music/Playlists")
        Files.createDirectories(baseDir)
        val m3u = baseDir.resolve("mixed.m3u")
        Files.writeString(
            m3u,
            """
            #EXTM3U
            ../Library/Notes/readme.txt
            ../Library/Notes/audio.unknown
            """.trimIndent()
        )

        val playlist = service.import(m3u)
        playlist.audioItems shouldHaveSize 0
    }

    "importAsync resolves to playlist via static fixture" {
        val fixture = M3uTestFixtures.loadPlaylist("Simple.m3u", files)
        preseed(fixture.tracks)

        val future = service.importAsync(fixture.rootPath, reactive.dispatcher)
        val playlist = future.get()

        playlist.shouldBeInstanceOf<MutableAudioPlaylist>()
        playlist.audioItems shouldHaveSize fixture.tracks.size
    }

    "importAsync cancellation surfaces CancellationException through CompletableFuture" {
        val fixture = M3uTestFixtures.loadPlaylist("Simple.m3u", files)
        preseed(fixture.tracks)

        val future: CompletableFuture<*> = service.importAsync(fixture.rootPath, reactive.dispatcher)
        future.cancel(true)

        // Cancellation may race with completion. If completion won, the result is a valid playlist
        // and no exception is surfaced; otherwise the future surfaces a CancellationException.
        if (!future.isCancelled) {
            future.get().shouldBeInstanceOf<MutableAudioPlaylist>()
        } else {
            shouldThrow<CancellationException> { future.get() }
        }
    }

    "constructor rejects negative maxDepth" {
        shouldThrow<IllegalArgumentException> { M3uImportService(library, maxDepth = -1) }
    }

    "reuses existing audio items by normalized absolute path" {
        val fixture = M3uTestFixtures.loadPlaylist("Simple.m3u", files)
        // Pre-seed manually to capture the item id of an existing entry.
        val firstSpy = M3uTestFixtures.materializeTrack(fixture.tracks.first(), files)
        val existing: AudioItem = library.audioItemFromFile(firstSpy)
        val sizeBeforeImport = library.audioLibrary().size()

        val playlist = service.import(fixture.rootPath)

        playlist.audioItems shouldHaveSize 1
        playlist.audioItems.first().id shouldBe existing.id
        library.audioLibrary().size() shouldBe sizeBeforeImport
    }
})