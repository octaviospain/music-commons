package net.transgressoft.commons.music

import net.transgressoft.commons.media.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.music.audio.AudioLibrary
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.playlist.AudioPlaylistMapSerializer
import net.transgressoft.commons.music.playlist.PlaylistHierarchy
import net.transgressoft.commons.music.playlist.asJsonKeyValues
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.commons.util.toJsonUri
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
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

    lateinit var audioFile: File
    lateinit var playlistsFile: File
    lateinit var waveformsFile: File

    lateinit var musicLibrary: CoreMusicLibrary

    lateinit var audioLibrary: AudioLibrary
    lateinit var waveforms: AudioWaveformRepository<AudioWaveform, AudioItem>
    lateinit var playlistHierarchy: PlaylistHierarchy

    beforeEach {
        audioFile = tempfile("audioLibrary-test", ".json").apply { deleteOnExit() }
        playlistsFile = tempfile("playlistHierarchy-test", ".json").apply { deleteOnExit() }
        waveformsFile = tempfile("waveformRepository-test", ".json").apply { deleteOnExit() }

        musicLibrary =
            CoreMusicLibrary.builder()
                .metadataIO(files.metadataIO)
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                .build()
        audioLibrary = musicLibrary.audioLibrary()
        playlistHierarchy = musicLibrary.playlistHierarchy()
        waveforms = musicLibrary.waveformRepository()
    }

    afterEach {
        musicLibrary.close()
    }

    "Operations on audio items impact subscribed repositories" {
        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())

        reactive.advance()

        // Compare via JSON key/value using the file:// URI form that toJsonUri() serializes.
        audioFile.readText().shouldContainJsonKeyValue("${audioItem.id}.path", audioItem.path.toJsonUri())
        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)

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

        audioLibrary.contains { it.title == "New title" }
        audioLibrary.size() shouldBe 1
        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)

        audioFile.readText() shouldContain "New title"
        val updatedPlaylist = playlistHierarchy.findByName("Test Playlist").get()
        updatedPlaylist.audioItems.contains(audioItem) shouldBe true

        audioLibrary.remove(audioItem) shouldBe true
        audioLibrary.isEmpty shouldBe true

        reactive.advance()

        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).isEmpty() shouldBe true
        audioFile.readText() shouldBe "{}"

        playlistHierarchy.findByName("Test Playlist") shouldBePresent {
            it.audioItems.isEmpty() shouldBe true
            playlistsFile.readText() shouldEqualJson listOf(it).asJsonKeyValues()
        }

        waveforms.isEmpty shouldBe true
        waveformsFile.readText() shouldBe "{}"
    }

    "Multi-item artist catalog sync — add items from multiple artists, remove one, verify partial catalog cleanup" {
        val artistA = ImmutableArtist.of("Artist Alpha")
        val artistB = ImmutableArtist.of("Artist Beta")
        val albumA1 = ImmutableAlbum("Album One", artistA)
        val albumA2 = ImmutableAlbum("Album Two", artistA)
        val albumB = ImmutableAlbum("Album Beta", artistB)

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
            // item1 is no longer in the registry — use referenceIds to avoid NoSuchElementException
            val refIds =
                (playlist.audioItems as? net.transgressoft.lirp.persistence.AggregateCollectionRef<*, *>)?.referenceIds?.map { it as Int } ?: emptyList()
            refIds.none { it == item1.id } shouldBe true
            refIds.any { it == item2.id } shouldBe true
        }
        playlistHierarchy.findByName("Playlist B") shouldBePresent { playlist ->
            val refIds =
                (playlist.audioItems as? net.transgressoft.lirp.persistence.AggregateCollectionRef<*, *>)?.referenceIds?.map { it as Int } ?: emptyList()
            refIds.none { it == item1.id } shouldBe true
            refIds.any { it == item2.id } shouldBe true
        }
    }

    "Lifecycle close integration — subscribe, close library, verify no further events propagate" {
        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        val playlist = playlistHierarchy.createPlaylist("Close Integration Playlist")
        playlist.addAudioItem(audioItem)
        reactive.advance()

        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).any { it.id == audioItem.id } shouldBe true

        audioLibrary.close()

        // After close, newly created items are no longer indexed in the artist catalog
        val item2 = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        audioLibrary.findAlbumAudioItems(item2.artist, item2.album.name).none { it.id == item2.id } shouldBe true
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
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
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