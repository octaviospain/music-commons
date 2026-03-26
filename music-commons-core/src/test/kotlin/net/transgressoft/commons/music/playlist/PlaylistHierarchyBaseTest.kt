package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.TestAudioLibrary
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class PlaylistHierarchyBaseTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var playlistHierarchy: TestPlaylistHierarchy

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        playlistHierarchy = TestPlaylistHierarchy()
    }

    afterEach {
        playlistHierarchy.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "PlaylistHierarchyBase creates playlist and stores in repository" {
        val playlist = playlistHierarchy.createPlaylist("Test Playlist")

        playlistHierarchy.findByName("Test Playlist") shouldBePresent { found ->
            found.name shouldBe "Test Playlist"
            found.id shouldBe playlist.id
            found.isDirectory shouldBe false
        }
    }

    "PlaylistHierarchyBase removes playlist from repository and hierarchy" {
        val playlist = playlistHierarchy.createPlaylist("Removable Playlist")

        playlistHierarchy.remove(playlist) shouldBe true

        playlistHierarchy.findByName("Removable Playlist").shouldBeEmpty()
    }

    "PlaylistHierarchyBase syncs audio item deletion across playlists" {
        val audioLibraryRepository = VolatileRepository<Int, net.transgressoft.commons.music.audio.AudioItem>("SyncTest")
        val audioLibrary = TestAudioLibrary(audioLibraryRepository)
        audioLibrary.subscribe(playlistHierarchy)

        val audioItem = audioLibrary.createFromFile(Arb.virtualAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        val playlist = playlistHierarchy.createPlaylist("Sync Test Playlist")
        playlist.addAudioItem(audioItem)
        testDispatcher.scheduler.advanceUntilIdle()

        playlist.audioItems.any { it.id == audioItem.id } shouldBe true

        audioLibrary.remove(audioItem) shouldBe true
        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.findByName("Sync Test Playlist") shouldBePresent { found ->
            found.audioItems.none { it.id == audioItem.id } shouldBe true
        }
    }

    "PlaylistHierarchyBase movePlaylist relocates between directories" {
        val dirA = playlistHierarchy.createPlaylistDirectory("Directory A")
        val dirB = playlistHierarchy.createPlaylistDirectory("Directory B")
        val playlist = playlistHierarchy.createPlaylist("Movable Playlist")
        playlistHierarchy.addPlaylistsToDirectory(setOf(playlist), dirA.name)

        playlistHierarchy.findByName("Directory A") shouldBePresent { dir ->
            dir.playlists.any { it.name == "Movable Playlist" } shouldBe true
        }

        playlistHierarchy.movePlaylist("Movable Playlist", "Directory B")

        playlistHierarchy.findByName("Directory A") shouldBePresent { dir ->
            dir.playlists.none { it.name == "Movable Playlist" } shouldBe true
        }
        playlistHierarchy.findByName("Directory B") shouldBePresent { dir ->
            dir.playlists.any { it.name == "Movable Playlist" } shouldBe true
        }
    }

    "PlaylistHierarchyBase close() cancels audio item event subscription" {
        val audioLibraryRepository = VolatileRepository<Int, net.transgressoft.commons.music.audio.AudioItem>("CloseTest")
        val audioLibrary = TestAudioLibrary(audioLibraryRepository)
        audioLibrary.subscribe(playlistHierarchy)

        val audioItem = audioLibrary.createFromFile(Arb.virtualAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        val playlist = playlistHierarchy.createPlaylist("Close Test Playlist")
        playlist.addAudioItem(audioItem)
        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.close()

        audioLibrary.remove(audioItem) shouldBe true
        testDispatcher.scheduler.advanceUntilIdle()

        // After close(), the audio item deletion event is no longer processed
        playlistHierarchy.findByName("Close Test Playlist") shouldBePresent { found ->
            found.audioItems.any { it.id == audioItem.id } shouldBe true
        }
    }

    "PlaylistHierarchyBase duplicate playlist name throws IllegalArgumentException" {
        playlistHierarchy.createPlaylist("Unique Name")

        shouldThrow<IllegalArgumentException> {
            playlistHierarchy.createPlaylist("Unique Name")
        }
    }
})