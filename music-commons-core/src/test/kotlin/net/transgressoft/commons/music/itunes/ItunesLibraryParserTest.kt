package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.audio.InvalidAudioFilePathException
import net.transgressoft.commons.music.audio.WindowsPathException
import net.transgressoft.commons.music.common.OsDetector
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Tests for [ItunesLibraryParser] covering parsing of tracks, playlists, smart playlist
 * filtering, missing location filtering, folder hierarchy, NSDate handling, and error cases.
 */
@DisplayName("ItunesLibraryParser")
internal class ItunesLibraryParserTest : StringSpec({

    val fixturePath = Paths.get(ItunesLibraryParserTest::class.java.getResource("/testfiles/itunes-library.xml")!!.toURI())

    "ItunesLibraryParser parses tracks from valid iTunes XML" {
        val library = ItunesLibraryParser.parse(fixturePath)

        library.tracks.size shouldBe 3
        val track = library.tracks[100]
        track.shouldNotBeNull()
        track.title shouldBe "Test Song"
        track.artist shouldBe "Test Artist"
        track.album shouldBe "Test Album"
        track.genre shouldBe "Rock"
        track.trackNumber shouldBe 1.toShort()
        track.totalTimeMs shouldBe 240000L
        track.location shouldBe "file:///music/test-song.mp3"
        track.playCount shouldBe 5.toShort()
    }

    "ItunesLibraryParser parses playlists from valid iTunes XML" {
        val library = ItunesLibraryParser.parse(fixturePath)

        library.playlists shouldHaveSize 4
        val playlist = library.playlists.find { it.name == "My Playlist" }
        playlist.shouldNotBeNull()
        playlist.persistentId shouldBe "PL001"
        playlist.trackIds shouldHaveSize 2
        playlist.trackIds[0] shouldBe 100
        playlist.trackIds[1] shouldBe 101
    }

    "ItunesLibraryParser skips smart playlists" {
        val library = ItunesLibraryParser.parse(fixturePath)

        library.playlists.none { it.name == "Smart Playlist" }.shouldBeTrue()
        library.playlists.none { it.persistentId == "PL003" }.shouldBeTrue()
    }

    "ItunesLibraryParser skips tracks without file location" {
        val library = ItunesLibraryParser.parse(fixturePath)

        library.tracks.containsKey(102) shouldBe false
        library.tracks.size shouldBe 3
    }

    "ItunesLibraryParser parses folder playlists with parentPersistentId" {
        val library = ItunesLibraryParser.parse(fixturePath)

        val folder = library.playlists.find { it.persistentId == "PL004" }
        folder.shouldNotBeNull()
        folder.isFolder.shouldBeTrue()

        val child = library.playlists.find { it.persistentId == "PL005" }
        child.shouldNotBeNull()
        child.parentPersistentId shouldBe "PL004"
    }

    "ItunesLibraryParser handles NSDate for dateAdded field" {
        val library = ItunesLibraryParser.parse(fixturePath)

        val track = library.tracks[100]
        track.shouldNotBeNull()
        val dateAdded = track.dateAdded
        dateAdded.shouldNotBeNull()
        dateAdded.year shouldBe 2020
        dateAdded.monthValue shouldBe 6
        dateAdded.dayOfMonth shouldBe 15
    }

    "ItunesLibraryParser.parse throws InvalidAudioFilePathException when xml file does not exist" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val nonExistent = fs.getPath("/missing/library.xml")
            val ex = shouldThrow<InvalidAudioFilePathException> { ItunesLibraryParser.parse(nonExistent) }
            ex.message!! shouldContain "does not exist"
        }
    }

    "ItunesLibraryParser.parse throws InvalidAudioFilePathException when xml path is a directory" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val dir = fs.getPath("/library-dir")
            Files.createDirectory(dir)
            val ex = shouldThrow<InvalidAudioFilePathException> { ItunesLibraryParser.parse(dir) }
            ex.message!! shouldContain "is not a regular file"
        }
    }

    "ItunesLibraryParser.parse throws InvalidAudioFilePathException when xml file is not readable"
        .config(enabled = !OsDetector.isWindows) {
            val tempFile = Files.createTempFile("unreadable-itunes", ".xml")
            try {
                tempFile.toFile().setReadable(false)
                val ex = shouldThrow<InvalidAudioFilePathException> { ItunesLibraryParser.parse(tempFile) }
                ex.message!! shouldContain "is not readable"
            } finally {
                tempFile.toFile().setReadable(true)
                Files.deleteIfExists(tempFile)
            }
        }

    "ItunesLibraryParser.parse throws WindowsPathException for a Windows-invalid xmlPath when isWindows=true" {
        OsDetector.withOverriddenIsWindows(true) {
            Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                val forbidden = fs.getPath("/tmp/bad|name.xml")
                shouldThrow<WindowsPathException> { ItunesLibraryParser.parse(forbidden) }
            }
        }
    }

    "ItunesLibraryParser.parse pass-through on Linux for xmlPath with Windows-only forbidden chars" {
        OsDetector.withOverriddenIsWindows(false) {
            Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                val path = fs.getPath("/itunes/bad|name.xml")
                val ex = shouldThrow<InvalidAudioFilePathException> { ItunesLibraryParser.parse(path) }
                ex.message!! shouldContain "does not exist"
            }
        }
    }
})