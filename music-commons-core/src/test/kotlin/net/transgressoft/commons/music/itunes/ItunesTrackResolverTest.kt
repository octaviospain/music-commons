package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.audio.AudioFileType
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files

@DisplayName("ItunesTrackResolver")
internal class ItunesTrackResolverTest : StringSpec({

    val resolver = ItunesTrackResolver()

    fun trackWithLocation(location: String): ItunesTrack =
        ItunesTrack(
            id = 1,
            title = "Test Track",
            artist = "Artist",
            albumArtist = "Artist",
            album = "Album",
            genre = null,
            year = null,
            trackNumber = null,
            discNumber = null,
            totalTimeMs = 0L,
            bitRate = 0,
            playCount = 0,
            rating = 0,
            bpm = null,
            comments = null,
            location = location,
            isCompilation = false,
            persistentId = null,
            dateAdded = null
        )

    "ItunesTrackResolver resolves NFD filename via NFC normalization" {
        // Explicit \u escapes so the compiler cannot collapse the two forms.
        // NFC: precomposed e-acute (U+00E9). NFD: e (U+0065) + combining acute (U+0301).
        val nfcName = "café.mp3"
        val nfdName = "café.mp3"
        check(nfdName != nfcName) { "NFD and NFC test inputs collapsed to the same string" }

        val tmpDir = Files.createTempDirectory("itunes-resolver-nfc-test")
        try {
            // Create the physical file using the NFC form
            val nfcFile = tmpDir.resolve(nfcName)
            Files.createFile(nfcFile)

            // Build a track whose location uses the NFD form (e + combining acute)
            val nfdLocation = tmpDir.resolve(nfdName).toUri().toString()
            val track = trackWithLocation(nfdLocation)

            val resolved = resolver.resolveTrackPath(track)

            // The resolver normalizes the filename to NFC
            resolved.fileName.toString() shouldBe nfcName
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    "ItunesTrackResolver resolveTrackPath is idempotent for ASCII input" {
        val tmpDir = Files.createTempDirectory("itunes-resolver-ascii-test")
        try {
            val asciiPath = tmpDir.resolve("song.mp3")
            val track = trackWithLocation(asciiPath.toUri().toString())

            val resolved = resolver.resolveTrackPath(track)

            resolved.fileName.toString() shouldBe "song.mp3"
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    "ItunesTrackResolver resolves path using non-default filesystem via getPath" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val jimfsResolver = ItunesTrackResolver(fs)
            // For jimfs (non-default) the resolver uses fileSystem.getPath(uri.path)
            val path = fs.getPath("/music/test.mp3")
            Files.createDirectories(path.parent)
            Files.createFile(path)

            val location = "file:///music/test.mp3"
            val track = trackWithLocation(location)

            val resolved = jimfsResolver.resolveTrackPath(track)

            resolved shouldNotBe null
            resolved.fileName.toString() shouldBe "test.mp3"
        }
    }

    "ItunesTrackResolver reports unsupported file type absent from policy" {
        val policy = ItunesImportPolicy(acceptedFileTypes = setOf(AudioFileType.MP3))
        val tmpDir = Files.createTempDirectory("itunes-resolver-unsupported-test")
        try {
            val oggPath = tmpDir.resolve("track.ogg")
            val result = resolver.isUnsupportedFileType(oggPath, policy)

            result shouldBe true
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    "ItunesTrackResolver accepts a supported file type present in policy" {
        val policy = ItunesImportPolicy(acceptedFileTypes = setOf(AudioFileType.MP3, AudioFileType.FLAC))
        val tmpDir = Files.createTempDirectory("itunes-resolver-supported-test")
        try {
            val mp3Path = tmpDir.resolve("track.mp3")
            val result = resolver.isUnsupportedFileType(mp3Path, policy)

            result shouldBe false
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    "ItunesTrackResolver reports unknown extension as unsupported" {
        val policy = ItunesImportPolicy()
        val tmpDir = Files.createTempDirectory("itunes-resolver-unknown-test")
        try {
            val unknownPath = tmpDir.resolve("track.xyz")
            val result = resolver.isUnsupportedFileType(unknownPath, policy)

            result shouldBe true
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }
})