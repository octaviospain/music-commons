package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.TestAudioLibrary
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.shouldNotReferenceItemId
import net.transgressoft.commons.music.shouldReferenceItemId
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.testing.registryIsolation
import net.transgressoft.lirp.persistence.RegistryBase.Companion.deregisterRepository
import net.transgressoft.lirp.persistence.RegistryBase.Companion.registerRepository
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class PlaylistHierarchyBaseTest : StringSpec({

    registryIsolation()
    val reactive = reactiveScope()
    val files = virtualFiles()
    lateinit var playlistHierarchy: TestPlaylistHierarchy

    beforeEach {
        playlistHierarchy = TestPlaylistHierarchy()
    }

    afterEach {
        playlistHierarchy.close()
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
        val audioLibraryRepository = VolatileRepository<Int, AudioItem>("SyncTest")
        deregisterRepository(AudioItem::class.java)
        registerRepository(AudioItem::class.java, audioLibraryRepository)
        val audioLibrary = TestAudioLibrary(audioLibraryRepository, files.metadataIO)
        audioLibrary.subscribe(playlistHierarchy)

        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        val playlist = playlistHierarchy.createPlaylist("Sync Test Playlist")
        playlist.addAudioItem(audioItem)
        reactive.advance()

        playlist.audioItems.map { it.id } shouldContain audioItem.id

        audioLibrary.remove(audioItem) shouldBe true
        reactive.advance()

        playlistHierarchy.findByName("Sync Test Playlist") shouldBePresent { found ->
            // Assert by reference id instead of iterating the proxy: the audio item was removed from
            // the registry so iterating would throw NoSuchElementException.
            found shouldNotReferenceItemId audioItem.id
        }
        audioLibrary.close()
        deregisterRepository(AudioItem::class.java)
    }

    "PlaylistHierarchyBase movePlaylist relocates between directories" {
        val dirA = playlistHierarchy.createPlaylistDirectory("Directory A")
        val dirB = playlistHierarchy.createPlaylistDirectory("Directory B")
        val playlist = playlistHierarchy.createPlaylist("Movable Playlist")
        playlistHierarchy.addPlaylistsToDirectory(setOf(playlist), dirA.name)

        playlistHierarchy.findByName("Directory A") shouldBePresent { dir ->
            dir.playlists.map { it.name } shouldContain "Movable Playlist"
        }

        playlistHierarchy.movePlaylist("Movable Playlist", "Directory B")

        playlistHierarchy.findByName("Directory A") shouldBePresent { dir ->
            dir.playlists.map { it.name } shouldNotContain "Movable Playlist"
        }
        playlistHierarchy.findByName("Directory B") shouldBePresent { dir ->
            dir.playlists.map { it.name } shouldContain "Movable Playlist"
        }
    }

    "PlaylistHierarchyBase close() cancels audio item event subscription" {
        val audioLibraryRepository = VolatileRepository<Int, AudioItem>("CloseTest")
        deregisterRepository(AudioItem::class.java)
        registerRepository(AudioItem::class.java, audioLibraryRepository)
        val audioLibrary = TestAudioLibrary(audioLibraryRepository, files.metadataIO)
        audioLibrary.subscribe(playlistHierarchy)

        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        val playlist = playlistHierarchy.createPlaylist("Close Test Playlist")
        playlist.addAudioItem(audioItem)
        reactive.advance()

        playlistHierarchy.close()

        audioLibrary.remove(audioItem) shouldBe true
        reactive.advance()

        // After close(), the audio item deletion event is no longer processed —
        // the playlist's audioItemIds still contains the id even though the audio item was removed from the library.
        // Access the captured playlist reference directly to avoid the use-after-close guard on findByName.
        playlist shouldReferenceItemId audioItem.id
        audioLibrary.close()
        deregisterRepository(AudioItem::class.java)
    }

    "PlaylistHierarchyBase duplicate playlist name throws IllegalArgumentException" {
        playlistHierarchy.createPlaylist("Unique Name")

        shouldThrow<IllegalArgumentException> {
            playlistHierarchy.createPlaylist("Unique Name")
        }
    }
})