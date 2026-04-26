package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.music.audio.WindowsPathException
import net.transgressoft.commons.music.common.OsDetector
import net.transgressoft.lirp.event.ReactiveScope
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class MusicLibraryTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "MusicLibrary builder creates volatile library by default" {
        val library = CoreMusicLibrary.builder().build()

        library.audioLibrary() shouldNotBe null
        library.playlistHierarchy() shouldNotBe null
        library.waveformRepository() shouldNotBe null

        library.close()
    }

    "MusicLibrary builder creates JSON-backed library" {
        val audioFile = tempfile("audioLibrary-test", ".json").apply { deleteOnExit() }
        val playlistsFile = tempfile("playlistHierarchy-test", ".json").apply { deleteOnExit() }
        val waveformsFile = tempfile("waveformRepository-test", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioLibraryJsonFile(audioFile)
                .playlistHierarchyJsonFile(playlistsFile)
                .waveformRepositoryJsonFile(waveformsFile)
                .build()

        val audioItem = library.audioItemFromFile(Arb.realAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        library.createPlaylist("Test Playlist")

        library.audioLibrary().size() shouldBe 1
        library.findPlaylistByName("Test Playlist") shouldBePresent { it.name shouldBe "Test Playlist" }

        library.close()
    }

    "MusicLibrary curated methods delegate to components" {
        val library = CoreMusicLibrary.builder().build()

        val audioItem = library.audioItemFromFile(Arb.virtualAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        library.createPlaylist("Curated Playlist")

        library.audioLibrary().size() shouldBe 1
        library.findPlaylistByName("Curated Playlist") shouldBePresent { it.name shouldBe "Curated Playlist" }
        library.findAlbumAudioItems(audioItem.artist, audioItem.album.name).any { it.id == audioItem.id } shouldBe true

        library.close()
    }

    "MusicLibrary close disposes all components" {
        val library = CoreMusicLibrary.builder().build()

        val audioItem = library.audioItemFromFile(Arb.virtualAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        val playlist = library.createPlaylist("Close Test Playlist")
        playlist.addAudioItem(audioItem)
        testDispatcher.scheduler.advanceUntilIdle()

        library.audioLibrary().findAlbumAudioItems(audioItem.artist, audioItem.album.name)
            .any { it.id == audioItem.id } shouldBe true

        library.close()

        // After close, newly created items are no longer indexed in the artist catalog
        val item2 = library.audioItemFromFile(Arb.virtualAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        library.audioLibrary().findAlbumAudioItems(item2.artist, item2.album.name)
            .none { it.id == item2.id } shouldBe true
    }

    "MusicLibrary persistence round-trip restores state from JSON files" {
        val audioFile = tempfile("audioLibrary-rt", ".json").apply { deleteOnExit() }
        val playlistsFile = tempfile("playlistHierarchy-rt", ".json").apply { deleteOnExit() }
        val waveformsFile = tempfile("waveformRepository-rt", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioLibraryJsonFile(audioFile)
                .playlistHierarchyJsonFile(playlistsFile)
                .waveformRepositoryJsonFile(waveformsFile)
                .build()

        val item1 = library.audioItemFromFile(Arb.realAudioFile().next())
        val item2 = library.audioItemFromFile(Arb.realAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        val playlist = library.createPlaylist("Round Trip Playlist")
        playlist.addAudioItems(listOf(item1, item2))
        testDispatcher.scheduler.advanceUntilIdle()

        val originalSize = library.audioLibrary().size()
        val originalPlaylistId = playlist.id
        val item1Id = item1.id
        val item2Id = item2.id

        library.close()

        val restoredLibrary =
            CoreMusicLibrary.builder()
                .audioLibraryJsonFile(audioFile)
                .playlistHierarchyJsonFile(playlistsFile)
                .waveformRepositoryJsonFile(waveformsFile)
                .build()
        testDispatcher.scheduler.advanceUntilIdle()

        restoredLibrary.audioLibrary().size() shouldBe originalSize

        restoredLibrary.findPlaylistByName("Round Trip Playlist") shouldBePresent { restoredPlaylist ->
            restoredPlaylist.id shouldBe originalPlaylistId
            restoredPlaylist.audioItems.any { it.id == item1Id } shouldBe true
            restoredPlaylist.audioItems.any { it.id == item2Id } shouldBe true
        }

        restoredLibrary.close()
    }

    "MusicLibrary.audioItemFromFile throws WindowsPathException before delegating when isWindows=true" {
        OsDetector.withOverriddenIsWindows(true) {
            CoreMusicLibrary.builder().build().use { library ->
                Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                    val forbidden = fs.getPath("/tmp/bad|name.mp3")
                    shouldThrow<WindowsPathException> { library.audioItemFromFile(forbidden) }
                }
            }
        }
    }
})