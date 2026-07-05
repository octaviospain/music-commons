package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioLibrary
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.playlist.PlaylistHierarchy
import net.transgressoft.commons.music.playlist.asJsonKeyValues
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.util.toJsonUri
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.arbitrary.next
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor

@ExperimentalCoroutinesApi
internal class MusicLibraryIntegrationTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    lateinit var repos: JsonRepoTriad
    lateinit var audioFile: File
    lateinit var playlistsFile: File
    lateinit var waveformsFile: File

    lateinit var musicLibrary: CoreMusicLibrary

    lateinit var audioLibrary: AudioLibrary
    lateinit var waveforms: AudioWaveformRepository<AudioWaveform, AudioItem>
    lateinit var playlistHierarchy: PlaylistHierarchy

    beforeEach {
        repos = jsonRepoTriad()
        audioFile = repos.audioFile
        playlistsFile = repos.playlistsFile
        waveformsFile = repos.waveformsFile

        musicLibrary =
            repos
                .wireInto(CoreMusicLibrary.builder().metadataIO(files.metadataIO))
                .build()
        audioLibrary = musicLibrary.audioLibrary()
        playlistHierarchy = musicLibrary.playlistHierarchy()
        waveforms = musicLibrary.waveformRepository()
    }

    afterEach {
        musicLibrary.close()
    }

    "Operations on audio items impact subscribed repositories" {
        // Deterministic artist and title so catalog lookups use Artist(name, UNDEFINED) consistently
        val itemArtist = Artist.of("Portishead")
        val itemAlbum = AlbumDetails("Dummy", itemArtist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = itemArtist
                    album = itemAlbum
                    title = "Glory Box"
                }.next()
            )

        reactive.advance()

        // Compare via JSON key/value using the file:// URI form that toJsonUri() serializes.
        audioFile.readText().shouldContainJsonKeyValue("${audioItem.id}.path", audioItem.path.toJsonUri())
        audioLibrary.findAlbumAudioItems(itemArtist, itemAlbum.name).shouldContainOnly(audioItem)

        val waveform = waveforms.getOrCreateWaveformAsync(audioItem, 780, 335, reactive.dispatcher.asExecutor())

        reactive.advance()

        waveform.get() shouldNotBe null
        waveform.get().id shouldBe audioItem.id
        waveformsFile.readText().shouldContainJsonKeyValue("${waveform.get().id}.audioFilePath", audioItem.path.toJsonUri())
        waveformsFile.readText() shouldContain waveform.get().id.toString()

        playlistHierarchy.createPlaylist("Test Playlist").also { it.addAudioItem(audioItem) }

        reactive.advance()

        playlistsFile.readText() shouldContain "Test Playlist"
        playlistsFile.readText() shouldContain audioItem.id.toString()

        audioItem.title = "New title"
        reactive.advance()

        audioLibrary.contains { it.title == "New title" } shouldBe true
        audioLibrary.size() shouldBe 1
        audioLibrary.findAlbumAudioItems(itemArtist, itemAlbum.name).shouldContainOnly(audioItem)

        audioFile.readText() shouldContain "New title"
        val updatedPlaylist = playlistHierarchy.findByName("Test Playlist").get()
        updatedPlaylist.audioItems.contains(audioItem) shouldBe true

        audioLibrary.remove(audioItem) shouldBe true
        audioLibrary.isEmpty shouldBe true

        reactive.advance()

        audioLibrary.findAlbumAudioItems(itemArtist, itemAlbum.name).isEmpty() shouldBe true
        audioFile.readText() shouldBe "{}"

        playlistHierarchy.findByName("Test Playlist") shouldBePresent {
            it.audioItems.isEmpty() shouldBe true
            playlistsFile.readText() shouldEqualJson listOf(it).asJsonKeyValues()
        }

        waveforms.isEmpty shouldBe true
        waveformsFile.readText() shouldBe "{}"
    }

    "Multi-item artist catalog sync — add items from multiple artists, remove one, verify partial catalog cleanup" {
        val artistA = Artist.of("Artist Alpha")
        val artistB = Artist.of("Artist Beta")
        val albumA1 = AlbumDetails("Album One", artistA)
        val albumA2 = AlbumDetails("Album Two", artistA)
        val albumB = AlbumDetails("Album Beta", artistB)

        val itemA1 =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = artistA
                    album = albumA1
                }.next()
            )
        val itemA2 =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = artistA
                    album = albumA2
                }.next()
            )
        val itemB =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = artistB
                    album = albumB
                }.next()
            )

        reactive.advance()

        audioLibrary.getArtistCatalog(artistA) shouldBePresent { it.artist shouldBe artistA }
        audioLibrary.getArtistCatalog(artistB) shouldBePresent { it.artist shouldBe artistB }
        audioLibrary.size() shouldBe 3

        audioLibrary.remove(itemB) shouldBe true
        reactive.advance()

        audioLibrary.getArtistCatalog(artistB).isEmpty shouldBe true
        audioLibrary.getArtistCatalog(artistA) shouldBePresent { catalog ->
            catalog.artist shouldBe artistA
            catalog.size shouldBe 2
        }
        audioLibrary.findAlbumAudioItems(artistA, albumA1.name).any { it.id == itemA1.id } shouldBe true
        audioLibrary.findAlbumAudioItems(artistA, albumA2.name).any { it.id == itemA2.id } shouldBe true
    }

    "Playlist multi-item lifecycle — multiple playlists sharing items, remove item, verify removal from all playlists" {
        val item1 = audioLibrary.createFromFile(files.virtualAudioFile().next())
        val item2 = audioLibrary.createFromFile(files.virtualAudioFile().next())

        reactive.advance()

        val playlistA = playlistHierarchy.createPlaylist("Playlist A")
        val playlistB = playlistHierarchy.createPlaylist("Playlist B")
        playlistA.addAudioItems(listOf(item1, item2))
        playlistB.addAudioItems(listOf(item1, item2))

        reactive.advance()

        audioLibrary.remove(item1) shouldBe true
        reactive.advance()

        playlistHierarchy.findByName("Playlist A") shouldBePresent { playlist ->
            // item1 is no longer in the registry — assert by reference id to avoid NoSuchElementException
            playlist shouldNotReferenceItemId item1.id
            playlist shouldReferenceItemId item2.id
        }
        playlistHierarchy.findByName("Playlist B") shouldBePresent { playlist ->
            playlist shouldNotReferenceItemId item1.id
            playlist shouldReferenceItemId item2.id
        }
    }

    "Lifecycle close integration — subscribe, close library, verify no further events propagate" {
        // Deterministic artist so catalog lookup by Artist works predictably
        val itemArtist = Artist.of("Nick Cave")
        val itemAlbum = AlbumDetails("Murder Ballads", itemArtist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = itemArtist
                    album = itemAlbum
                    title = "The Curse Of Millhaven"
                }.next()
            )
        reactive.advance()

        val playlist = playlistHierarchy.createPlaylist("Close Integration Playlist")
        playlist.addAudioItem(audioItem)
        reactive.advance()

        audioLibrary.findAlbumAudioItems(itemArtist, itemAlbum.name).any { it.id == audioItem.id } shouldBe true

        audioLibrary.close()

        // After close, newly created items are no longer indexed in the artist catalog
        val item2 = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        audioLibrary shouldNotIndex item2
        // Playlist still holds audioItem because playlist hierarchy subscription is separate
        playlistHierarchy.findByName("Close Integration Playlist") shouldBePresent {
            it.audioItems.any { item -> item.id == audioItem.id } shouldBe true
        }
    }

    "Persistence round-trip — create items, close repositories, reopen from same JSON files, verify full state restoration" {
        val item1 = audioLibrary.createFromFile(files.virtualAudioFile().next())
        val item2 = audioLibrary.createFromFile(files.virtualAudioFile().next())

        reactive.advance()

        val playlist = playlistHierarchy.createPlaylist("Persisted Playlist")
        playlist.addAudioItems(listOf(item1, item2))

        reactive.advance()

        val originalSize = audioLibrary.size()
        val originalPlaylistId = playlist.id
        val item1Id = item1.id
        val item2Id = item2.id

        musicLibrary.close()

        val restoredLibrary =
            repos.reopen()
                .wireInto(CoreMusicLibrary.builder())
                .build()
        reactive.advance()

        restoredLibrary.audioLibrary().size() shouldBe originalSize

        restoredLibrary.playlistHierarchy().findByName("Persisted Playlist") shouldBePresent { restoredPlaylist ->
            restoredPlaylist.id shouldBe originalPlaylistId
            restoredPlaylist.audioItems.any { it.id == item1Id } shouldBe true
            restoredPlaylist.audioItems.any { it.id == item2Id } shouldBe true
        }

        restoredLibrary.close()
    }
})