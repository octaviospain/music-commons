package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Alternative
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.Blues
import net.transgressoft.commons.music.audio.Classical
import net.transgressoft.commons.music.audio.Electronic
import net.transgressoft.commons.music.audio.Folk
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.HipHop
import net.transgressoft.commons.music.audio.JAudioTaggerMetadataIO
import net.transgressoft.commons.music.audio.Jazz
import net.transgressoft.commons.music.audio.Label
import net.transgressoft.commons.music.audio.Metal
import net.transgressoft.commons.music.audio.Pop
import net.transgressoft.commons.music.audio.Punk
import net.transgressoft.commons.music.audio.Reggae
import net.transgressoft.commons.music.audio.Rock
import net.transgressoft.commons.music.audio.audioItemChange
import net.transgressoft.commons.music.audio.testCoverBytes
import net.transgressoft.commons.music.audio.update
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.util.InvalidAudioFilePathException
import net.transgressoft.commons.util.OsDetector
import net.transgressoft.commons.util.WindowsPathException
import net.transgressoft.lirp.persistence.VolatileRepository
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.date.shouldNotBeBefore
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import javafx.scene.image.Image
import org.testfx.util.WaitForAsyncUtils
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Optional
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class FXAudioItemTest : StringSpec({
    val files = virtualFiles()

    "Changes its properties when observable properties are updated" {
        val path = files.virtualAudioFile { coverImageBytes = testCoverBytes }.next()

        val fxAudioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
        assertSoftly {
            fxAudioItem.titleProperty.value shouldBe fxAudioItem.title
            fxAudioItem.artistProperty.value shouldBe fxAudioItem.artist
            fxAudioItem.albumProperty.value shouldBe fxAudioItem.album
            fxAudioItem.genresProperty.value shouldBe fxAudioItem.genres
            fxAudioItem.commentsProperty.value shouldBe (fxAudioItem.comments ?: "")
            fxAudioItem.trackNumberProperty.value shouldBe (fxAudioItem.trackNumber?.toInt() ?: -1)
            fxAudioItem.discNumberProperty.value shouldBe (fxAudioItem.discNumber?.toInt() ?: -1)
            fxAudioItem.bpmProperty.value shouldBe (fxAudioItem.bpm ?: -1f)
            fxAudioItem.coverImageProperty.value shouldBePresent {
                it.height shouldBe Image(ByteArrayInputStream(fxAudioItem.coverImageBytes)).height
            }
            fxAudioItem.artistsInvolvedProperty.value shouldBe fxAudioItem.artistsInvolved
            fxAudioItem.lastDateModifiedProperty.value shouldBe fxAudioItem.lastDateModified
            fxAudioItem.dateOfCreationProperty.value shouldBeSameInstanceAs fxAudioItem.dateOfCreation
        }

        var lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.titleProperty.set("new title")
        eventually(100.milliseconds) {
            fxAudioItem.title shouldBe "new title"
            fxAudioItem.lastDateModified shouldNotBeBefore lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldNotBeBefore lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.artistProperty.set(Artist.of("Bon Jovi"))
        eventually(100.milliseconds) {
            fxAudioItem.artist.name shouldBe "Bon Jovi"
            fxAudioItem.artistsInvolved shouldContain Artist.of("Bon Jovi")
            fxAudioItem.lastDateModified shouldNotBeBefore lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldNotBeBefore lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.albumProperty.set(Album("New Album", Artist.of("Bon Jovi"), false, 2021, Label.UNKNOWN))
        eventually(100.milliseconds) {
            fxAudioItem.album.name shouldBe "New Album"
            fxAudioItem.album.albumArtist.name shouldBe "Bon Jovi"
            fxAudioItem.lastDateModified shouldNotBeBefore lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldNotBeBefore lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        val newGenres = fxAudioItem.genres.randomDifferent()
        fxAudioItem.genresProperty.set(newGenres)
        eventually(100.milliseconds) {
            fxAudioItem.genres shouldBe newGenres
            fxAudioItem.genresProperty.value shouldBe newGenres
            fxAudioItem.lastDateModified shouldNotBeBefore lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldNotBeBefore lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.comments = "New comments"
        eventually(100.milliseconds) {
            fxAudioItem.commentsProperty.value shouldBe "New comments"
            fxAudioItem.lastDateModified shouldNotBeBefore lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldNotBeBefore lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.trackNumber = 5
        eventually(100.milliseconds) {
            fxAudioItem.trackNumberProperty.value shouldBe 5
            fxAudioItem.lastDateModified shouldNotBeBefore lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldNotBeBefore lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.discNumber = 2
        eventually(100.milliseconds) {
            fxAudioItem.discNumberProperty.value shouldBe 2
            fxAudioItem.lastDateModified shouldNotBeBefore lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldNotBeBefore lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.bpm = 130f
        eventually(100.milliseconds) {
            fxAudioItem.bpmProperty.value shouldBe 130f
            fxAudioItem.lastDateModified shouldNotBeBefore lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldNotBeBefore lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.coverImageBytes = null
        eventually(100.milliseconds) {
            fxAudioItem.coverImageBytes shouldBe null
            fxAudioItem.coverImageProperty.value shouldBe Optional.empty()
            fxAudioItem.lastDateModified shouldNotBeBefore lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldNotBeBefore lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.incrementPlayCount()
        eventually(500.milliseconds) {
            fxAudioItem.playCount shouldBe 1
            fxAudioItem.playCountProperty.value shouldBe 1
            fxAudioItem.lastDateModified shouldNotBeBefore lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldNotBeBefore lastDateUpdated
        }
    }

    "Creates an audio item and writes changes to metadata" {
        val testAudioFile = Arb.realAudioFile().next()
        val fxAudioItem = FXAudioItemTestBridge.createFxAudioItem(testAudioFile)

        val audioItemChanges = Arb.audioItemChange().next()
        fxAudioItem.update(audioItemChanges)

        JAudioTaggerMetadataIO().writeMetadata(fxAudioItem)

        val loadedAudioItem = FXAudioItemTestBridge.createFxAudioItem(testAudioFile, fxAudioItem.id)
        assertSoftly {
            loadedAudioItem.id shouldBe fxAudioItem.id
            loadedAudioItem.dateOfCreation shouldNotBeBefore fxAudioItem.dateOfCreation
            loadedAudioItem.lastDateModified shouldNotBeBefore fxAudioItem.lastDateModified
            loadedAudioItem.path shouldBe fxAudioItem.path
            loadedAudioItem.fileName shouldBe fxAudioItem.fileName
            loadedAudioItem.extension shouldBe fxAudioItem.extension
            loadedAudioItem.title shouldBe fxAudioItem.title
            loadedAudioItem.duration shouldBe fxAudioItem.duration
            loadedAudioItem.bitRate shouldBe fxAudioItem.bitRate
            loadedAudioItem.encoder shouldBe fxAudioItem.encoder
            loadedAudioItem.encoding shouldBe fxAudioItem.encoding
            loadedAudioItem.artist shouldBe fxAudioItem.artist
            loadedAudioItem.album.albumArtist.name shouldBe fxAudioItem.album.albumArtist.name
            loadedAudioItem.album.albumArtist.countryCode shouldBe CountryCode.UNDEFINED // album country code is not updated because there is no ID3 tag for it
            loadedAudioItem.album.isCompilation shouldBe fxAudioItem.album.isCompilation
            loadedAudioItem.album.label.name shouldBe fxAudioItem.album.label.name
            loadedAudioItem.album.label.countryCode shouldBe CountryCode.UNDEFINED // label country code is not updated because there is no ID3 tag for it
            loadedAudioItem.artist.name shouldBe fxAudioItem.artist.name
            loadedAudioItem.artist.countryCode shouldBe fxAudioItem.artist.countryCode // artist country code is saved into COUNTRY ID3 tag
            loadedAudioItem.genres shouldBe fxAudioItem.genres
            loadedAudioItem.comments shouldBe fxAudioItem.comments
            loadedAudioItem.trackNumber shouldBe fxAudioItem.trackNumber
            loadedAudioItem.discNumber shouldBe fxAudioItem.discNumber
            if (testAudioFile.toString().endsWith(".m4a")) {
                loadedAudioItem.bpm shouldBe fxAudioItem.bpm?.toInt()?.toFloat()
            } else {
                loadedAudioItem.bpm shouldBe fxAudioItem.bpm
            }
            loadedAudioItem.coverImageBytes shouldBe fxAudioItem.coverImageBytes
            loadedAudioItem.playCount shouldBe fxAudioItem.playCount
            loadedAudioItem.uniqueId shouldBe fxAudioItem.uniqueId
            loadedAudioItem.toString() shouldBe fxAudioItem.toString()
        }
    }

    "FXAudioItem getter returns the value that was set" {
        val fxAudioItem = FXAudioItemTestBridge.createFxAudioItem(Arb.realAudioFile().next())
        fxAudioItem.coverImageBytes = testCoverBytes

        fxAudioItem.coverImageBytes shouldBeSameInstanceAs testCoverBytes
    }

    "FXAudioItem setter stores the provided reference directly" {
        val fxAudioItem = FXAudioItemTestBridge.createFxAudioItem(Arb.realAudioFile().next())
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        fxAudioItem.coverImageBytes = bytes

        fxAudioItem.coverImageBytes!![0] shouldBe 1.toByte()
        fxAudioItem.coverImageBytes shouldBeSameInstanceAs bytes
    }

    "FXAudioItem clone shares the cover ByteArray reference with the original" {
        val fxAudioItem = FXAudioItemTestBridge.createFxAudioItem(Arb.realAudioFile().next())
        fxAudioItem.coverImageBytes = testCoverBytes

        val cloned = fxAudioItem.clone()

        cloned.coverImageBytes shouldBeSameInstanceAs fxAudioItem.coverImageBytes
    }

    "FXAudioItem.coverImageBytes lazy-loads via metadataIO back-ref on first read and caches result" {
        // Construct an orphan item via the deserialization constructor (metadataIO starts null,
        // cover unseeded). Manually wire the metadataIO back-ref to verify the lazy-load path.
        val path = Arb.realAudioFile().next()
        val item =
            FXAudioItem(
                path,
                1, AudioItemMetadata(),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                0
            )

        // Without metadataIO wired, coverImageBytes returns null even when the file has artwork.
        item.coverImageBytes shouldBe null

        val metadataIO = JAudioTaggerMetadataIO()
        val expected = metadataIO.loadCover(item)
        // Reconstruct a fresh item to model the rehydration path cleanly.
        val freshItem =
            FXAudioItem(
                path,
                2, AudioItemMetadata(),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                0
            )
        freshItem.metadataIO = metadataIO

        val firstRead = freshItem.coverImageBytes
        firstRead shouldBe expected

        val secondRead = freshItem.coverImageBytes
        secondRead shouldBe expected
    }

    "FXAudioItem.coverImageProperty triggers lazy cover load on first observation and resolves to a present image" {
        // Model the rehydration path: item created without pre-seeded cover bytes, metadataIO wired
        // afterward. The getter is never called before observing the property — the cover must
        // populate through the property path alone.
        // Seed the real audio file with embedded cover art so the lazy load has artwork to decode.
        val path = Arb.realAudioFile { coverImageBytes = testCoverBytes }.next()
        val freshItem =
            FXAudioItem(
                path,
                2,
                AudioItemMetadata(),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                0
            )
        freshItem.metadataIO = JAudioTaggerMetadataIO()

        // Simulate a UI binding: attach a listener to trigger the first observation.
        // The FXAudioItem.runOnFxThread fallback executes the property update inline when the
        // JavaFX toolkit has not been started, so the property is already populated after
        // addListener returns in this headless test context.
        freshItem.coverImageProperty.addListener { _, _, _ -> }

        // In the headless test context, FXAudioItem.runOnFxThread falls back to inline execution
        // when the JavaFX toolkit has not been started, so the property update is synchronous.
        // When a toolkit IS running (e.g. in an FX-enabled test container), waitForFxEvents()
        // drains the FX queue before asserting.
        eventually(2.seconds) {
            try {
                WaitForAsyncUtils.waitForFxEvents()
            } catch (_: Exception) {
                // Toolkit not initialized — the inline fallback already populated the property.
            }
            freshItem.coverImageProperty.value shouldBePresent {
                (it.height > 0.0) shouldBe true
                (it.width > 0.0) shouldBe true
            }
        }

        // Second observation must not re-probe or throw — the property stays present.
        freshItem.coverImageProperty.addListener { _, _, _ -> }
        freshItem.coverImageProperty.value shouldBePresent { (it.height > 0.0) shouldBe true }
    }

    "FXAudioLibrary.createFromFile throws InvalidAudioFilePathException when file does not exist" {
        val nonExistent = Paths.get("/tmp/fx-nonexistent-${System.nanoTime()}.mp3")
        FXAudioLibrary(VolatileRepository("FXAudioLibrary"), files.metadataIO).use { library ->
            val ex = shouldThrow<InvalidAudioFilePathException> { library.createFromFile(nonExistent) }
            ex.message!! shouldContain "does not exist"
        }
    }

    "FXAudioLibrary.createFromFile throws InvalidAudioFilePathException when path is a directory" {
        val dir = Files.createTempDirectory("fx-dir-test")
        try {
            FXAudioLibrary(VolatileRepository("FXAudioLibrary"), files.metadataIO).use { library ->
                val ex = shouldThrow<InvalidAudioFilePathException> { library.createFromFile(dir) }
                ex.message!! shouldContain "is not a regular file"
            }
        } finally {
            Files.deleteIfExists(dir)
        }
    }

    "FXAudioLibrary.createFromFile InvalidAudioFilePathException is catchable" {
        val nonExistent = Paths.get("/tmp/fx-nonexistent-${System.nanoTime()}.mp3")
        FXAudioLibrary(VolatileRepository("FXAudioLibrary"), files.metadataIO).use { library ->
            shouldThrow<InvalidAudioFilePathException> { library.createFromFile(nonExistent) }
        }
    }

    "FXAudioItem throws WindowsPathException for a reserved-name path when isWindows=true" {
        OsDetector.withOverriddenIsWindows(true) {
            // Jimfs windows configuration: separator is `\` so the validator engages. Unix-style
            // Jimfs paths bypass validation per the Phase 40 fix (they don't reach Win32 IO).
            // Jimfs windows rejects most forbidden chars at parse time, so use a reserved name
            // (which parses fine but the validator still rejects) to exercise the validation path.
            val fs = Jimfs.newFileSystem(Configuration.windows())
            fs.use { fs ->
                val forbidden = fs.getPath("C:\\tmp\\NUL.mp3")
                shouldThrow<WindowsPathException> { FXAudioItem(forbidden, metadata = AudioItemMetadata()) }
            }
        }
    }

    "FXAudioLibrary.createFromFile pass-through when isWindows=false for a path with Windows-forbidden chars" {
        OsDetector.withOverriddenIsWindows(false) {
            val fs = Jimfs.newFileSystem(Configuration.unix())
            fs.use { fs ->
                val forbidden = fs.getPath("/tmp/fx-nonexistent-bad|name-${System.nanoTime()}.mp3")
                FXAudioLibrary(VolatileRepository("FXAudioLibrary"), files.metadataIO).use { library ->
                    val ex = shouldThrow<InvalidAudioFilePathException> { library.createFromFile(forbidden) }
                    ex.message!! shouldContain "does not exist"
                }
            }
        }
    }
})

fun Set<Genre>.randomDifferent(): Set<Genre> {
    val knownGenres =
        listOf(
            Rock, Alternative, Jazz, Blues,
            Electronic, HipHop, Classical, Folk,
            Metal, Pop, Punk, Reggae
        )
    val differentGenre = knownGenres.first { it !in this }
    return setOf(differentGenre)
}