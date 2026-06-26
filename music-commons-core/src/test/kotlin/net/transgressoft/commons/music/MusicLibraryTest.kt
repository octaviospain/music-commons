package net.transgressoft.commons.music

import net.transgressoft.commons.media.persistence.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.persistence.music.playlist.AudioPlaylistMapSerializer
import net.transgressoft.commons.util.OsDetector
import net.transgressoft.commons.util.WindowsPathException
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class MusicLibraryTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

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
                .metadataIO(files.metadataIO)
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                .build()

        val audioItem = library.audioItemFromFile(files.virtualAudioFile().next())
        reactive.advance()

        library.createPlaylist("Test Playlist")

        library.audioLibrary().size() shouldBe 1
        library.findPlaylistByName("Test Playlist") shouldBePresent { it.name shouldBe "Test Playlist" }

        library.close()
    }

    "MusicLibrary curated methods delegate to components" {
        val library = CoreMusicLibrary.builder().metadataIO(files.metadataIO).build()

        // Deterministic artist (no country code) so catalog key matches the name-derived Artist
        val artist = Artist.of("Portishead")
        val album = AlbumDetails("Dummy", artist)
        val audioItem =
            library.audioItemFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Glory Box"
                }.next()
            )
        reactive.advance()

        library.createPlaylist("Curated Playlist")

        library.audioLibrary().size() shouldBe 1
        library.findPlaylistByName("Curated Playlist") shouldBePresent { it.name shouldBe "Curated Playlist" }
        library.findAlbumAudioItems(artist, album.name).any { it.id == audioItem.id } shouldBe true

        library.close()
    }

    "MusicLibrary close disposes all components" {
        val library = CoreMusicLibrary.builder().metadataIO(files.metadataIO).build()

        val artist = Artist.of("Massive Attack")
        val album = AlbumDetails("Mezzanine", artist)
        val audioItem =
            library.audioItemFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Teardrop"
                }.next()
            )
        reactive.advance()

        val playlist = library.createPlaylist("Close Test Playlist")
        playlist.addAudioItem(audioItem)
        reactive.advance()

        library.audioLibrary().findAlbumAudioItems(artist, album.name)
            .any { it.id == audioItem.id } shouldBe true

        library.close()

        // After close, newly created items are no longer indexed in the artist catalog
        val item2 = library.audioItemFromFile(files.virtualAudioFile().next())
        reactive.advance()

        library.audioLibrary().findAlbumAudioItems(Artist.of(item2.artist.name), item2.album.name)
            .none { it.id == item2.id } shouldBe true
    }

    "MusicLibrary persistence round-trip restores state from JSON files" {
        val audioFile = tempfile("audioLibrary-rt", ".json").apply { deleteOnExit() }
        val playlistsFile = tempfile("playlistHierarchy-rt", ".json").apply { deleteOnExit() }
        val waveformsFile = tempfile("waveformRepository-rt", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .metadataIO(files.metadataIO)
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                .build()

        val item1 = library.audioItemFromFile(files.virtualAudioFile().next())
        val item2 = library.audioItemFromFile(files.virtualAudioFile().next())
        reactive.advance()

        val playlist = library.createPlaylist("Round Trip Playlist")
        playlist.addAudioItems(listOf(item1, item2))
        reactive.advance()

        val originalSize = library.audioLibrary().size()
        val originalPlaylistId = playlist.id
        val item1Id = item1.id
        val item2Id = item2.id

        library.close()

        val restoredLibrary =
            CoreMusicLibrary.builder()
                .metadataIO(files.metadataIO)
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .waveformRepository(JsonFileRepository(waveformsFile, AudioWaveformMapSerializer))
                .build()
        reactive.advance()

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
                // Jimfs windows configuration so the path's filesystem separator is `\` and the
                // validator engages. Jimfs unix paths bypass validation per the Phase 40 fix that
                // skips the validator for non-Windows-style filesystems.
                Jimfs.newFileSystem(Configuration.windows()).use { fs ->
                    // Jimfs windows rejects forbidden chars at parse, so use a reserved name.
                    val forbidden = fs.getPath("C:\\tmp\\NUL.mp3")
                    shouldThrow<WindowsPathException> { library.audioItemFromFile(forbidden) }
                }
            }
        }
    }
})