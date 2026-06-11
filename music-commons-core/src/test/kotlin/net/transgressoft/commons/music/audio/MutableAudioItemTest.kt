package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.FLAC
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.MP4_INFO
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import net.transgressoft.commons.music.audio.JAudioTaggerMetadataIO
import net.transgressoft.commons.music.audio.MutableAudioItemTestBridge.createAudioItem
import net.transgressoft.commons.util.InvalidAudioFilePathException
import net.transgressoft.commons.util.OsDetector
import net.transgressoft.commons.util.WindowsPathException
import net.transgressoft.lirp.persistence.VolatileRepository
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.date.shouldNotBeBefore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.nio.file.Files
import java.time.Duration
import java.time.LocalDateTime
import kotlin.io.path.extension
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.Json

@Isolate
internal class MutableAudioItemTest : FunSpec({

    val json =
        Json {
            prettyPrint = true
            explicitNulls = true
            allowStructuredMapKeys = true
        }

    val expectedAttributes =
        Arb.audioAttributes(
            title = "Yesterday",
            duration = Duration.ofSeconds(180),
            bitRate = 320,
            artist = Artist.of("The Beatles", CountryCode.UK),
            album =
                Album(
                    "Help!",
                    Artist.of("The Beatles Band", CountryCode.UK), true, 1965, Label.of("EMI", CountryCode.US)
                ),
            bpm = 120f,
            trackNumber = 13,
            discNumber = 1,
            comments = "Best song ever!",
            genres = setOf(Rock),
            encoder = "transgressoft",
            coverImageBytes = testCoverBytes,
            playCount = 0
        ).next()

    context("Can be created, serialized to json, and and write changes to file metadata from") {
        withData(
            mapOf(
                "a mp3 file" to Arb.realAudioFile(ID3_V_24, expectedAttributes).next(),
                "a m4a file" to Arb.realAudioFile(MP4_INFO, expectedAttributes).next(),
                "a wav file" to Arb.realAudioFile(WAV, expectedAttributes).next(),
                "a flac file" to Arb.realAudioFile(FLAC, expectedAttributes).next()
                // Note: OGG/VorbisComment is excluded from metadata round-trip tests because
                // jaudiotagger cannot re-read OGG files with vorbis-comment headers after writing.
                // OGG is tested separately for playback and waveform generation.
            )
        ) { filePath ->
            val date = LocalDateTime.now()
            val audioItem =
                createAudioItem(filePath, UNASSIGNED_ID).apply {
                    path shouldBe filePath
                    fileName shouldBe filePath.fileName.toString()
                    length shouldBe filePath.toFile().length()
                    extension shouldBe filePath.extension

                    dateOfCreation shouldBeAfter date
                    lastDateModified shouldBe dateOfCreation

                    this shouldMatch
                        expectedAttributes.apply {
                            // when a MutableAudioItem is created outside a repository it does not have and ID
                            id = UNASSIGNED_ID
                            // the album artists' and label's country code is not saved in the ID3 tag
                            album =
                                Album(
                                    "Help!",
                                    Artist.of("The Beatles Band", CountryCode.UNDEFINED), true, 1965, Label.of("EMI", CountryCode.UNDEFINED)
                                )
                        }
                }

            // Test toString() and compareTo() for coverage
            audioItem.album.toString() shouldContain "Album"
            audioItem.album.toString() shouldContain "Help!"
            audioItem.artist.toString() shouldContain "The Beatles"
            audioItem.album.compareTo(Album.UNKNOWN) shouldNotBe 0
            audioItem.artist.compareTo(Artist.UNKNOWN) shouldNotBe 0
            audioItem.album.label.compareTo(Label.UNKNOWN) shouldNotBe 0

            // Test AudioFileType extension and toString() for coverage
            val fileExtension = audioItem.path.extension
            audioItem.path.extension.toAudioFileType().toString() shouldBe fileExtension
            audioItem.path.extension.toAudioFileType().extension shouldBe fileExtension

            json.encodeToString(AudioItemSerializer(), audioItem).let {
                it shouldEqualJson audioItem.asJsonValue()
                (json.decodeFromString(AudioItemSerializer(), it) as MutableAudioItem) shouldBe audioItem
            }

            val audioItemChanges = Arb.audioItemChange().next()
            audioItem.update(audioItemChanges)

            JAudioTaggerMetadataIO().writeMetadata(audioItem)
            val loadedAudioItem: AudioItem = createAudioItem(filePath, audioItem.id)

            assertSoftly {
                loadedAudioItem.id shouldBe audioItem.id
                loadedAudioItem.dateOfCreation shouldNotBeBefore audioItem.dateOfCreation
                loadedAudioItem.lastDateModified shouldNotBeBefore audioItem.lastDateModified
                loadedAudioItem.path shouldBe audioItem.path
                loadedAudioItem.fileName shouldBe audioItem.fileName
                loadedAudioItem.extension shouldBe audioItem.extension
                loadedAudioItem.title shouldBe audioItem.title
                loadedAudioItem.duration shouldBe audioItem.duration
                loadedAudioItem.bitRate shouldBe audioItem.bitRate
                // album country code is not updated because there is no ID3 tag for it
                loadedAudioItem.album.albumArtist.name shouldBe audioItem.album.albumArtist.name
                loadedAudioItem.album.albumArtist.countryCode shouldBe CountryCode.UNDEFINED
                loadedAudioItem.album.isCompilation shouldBe audioItem.album.isCompilation
                // label country code is not updated because there is no ID3 tag for it
                loadedAudioItem.album.label.name shouldBe audioItem.album.label.name
                loadedAudioItem.album.label.countryCode shouldBe CountryCode.UNDEFINED
                // artist country code is saved into COUNTRY ID3 tag
                loadedAudioItem.artist.name shouldBe audioItem.artist.name
                loadedAudioItem.artist.countryCode shouldBe audioItem.artist.countryCode
                if (filePath.extension == "m4a") {
                    loadedAudioItem.bpm shouldBe audioItem.bpm?.toInt()?.toFloat()
                } else {
                    loadedAudioItem.bpm shouldBe audioItem.bpm
                }
                loadedAudioItem.trackNumber shouldBe audioItem.trackNumber
                loadedAudioItem.discNumber shouldBe audioItem.discNumber
                loadedAudioItem.comments shouldBe audioItem.comments
                // JAudioTagger may reformat custom genre strings during write-read round-trips,
                // so compare by genre name rather than exact Genre object equality
                loadedAudioItem.genres.map { it.name }.toSet() shouldBe audioItem.genres.map { it.name }.toSet()
                loadedAudioItem.encoding shouldBe audioItem.encoding
                loadedAudioItem.encoder shouldBe audioItem.encoder
                loadedAudioItem.playCount shouldBe audioItem.playCount
                loadedAudioItem.coverImageBytes shouldBe audioItem.coverImageBytes
                loadedAudioItem.uniqueId shouldBe audioItem.uniqueId
                loadedAudioItem.toString() shouldBe audioItem.toString()
            }
        }
    }

    context("coverImageBytes is null after deserialization (cover travels out of band)") {
        val audioItem =
            createAudioItem(
                Arb.realAudioFile(ID3_V_24) {
                    coverImageBytes = null
                }.next()
            )

        audioItem.coverImageBytes shouldBe null

        audioItem.coverImageBytes = testCoverBytes

        JAudioTaggerMetadataIO().writeMetadata(audioItem)

        // Option A semantics (Phase 40-02): deserialized items always start with null cover bytes;
        // covers are re-loaded by the audio library via library.loadCover(item) added in plan 40-04.
        eventually(200.milliseconds) {
            val encodedAudioItem = json.encodeToString(AudioItemSerializer(), audioItem)
            val decodedAudioItem = json.decodeFromString(AudioItemSerializer(), encodedAudioItem) as MutableAudioItem

            decodedAudioItem.coverImageBytes shouldBe null
        }
    }

    context("MutableAudioItem coverImageBytes defensive copy") {
        test("MutableAudioItem getter returns defensive copy — mutating returned array does not affect internal state") {
            val audioItem = createAudioItem(Arb.realAudioFile(ID3_V_24).next())
            audioItem.coverImageBytes = testCoverBytes
            val returned = audioItem.coverImageBytes!!
            val originalContent = returned.copyOf()
            returned[0] = 0x00.toByte()

            audioItem.coverImageBytes!! shouldBe originalContent
        }

        test("MutableAudioItem setter stores defensive copy — mutating source array after set does not affect internal state") {
            val audioItem = createAudioItem(Arb.realAudioFile(ID3_V_24).next())
            val source = byteArrayOf(1, 2, 3, 4, 5)
            audioItem.coverImageBytes = source
            source[0] = 99.toByte()

            audioItem.coverImageBytes!![0] shouldBe 1.toByte()
        }
    }

    context("MutableAudioItem.bpm rejects non-finite Float values") {
        test("setting bpm = NaN throws IllegalArgumentException") {
            val audioItem = createAudioItem(Arb.realAudioFile(ID3_V_24).next())
            shouldThrow<IllegalArgumentException> { audioItem.bpm = Float.NaN }
        }

        test("setting bpm = POSITIVE_INFINITY throws IllegalArgumentException") {
            val audioItem = createAudioItem(Arb.realAudioFile(ID3_V_24).next())
            shouldThrow<IllegalArgumentException> { audioItem.bpm = Float.POSITIVE_INFINITY }
        }

        test("AudioItemMetadata init rejects NaN bpm") {
            shouldThrow<IllegalArgumentException> { AudioItemMetadata(bpm = Float.NaN) }
        }
    }

    context("DefaultAudioLibrary.createFromFile three-check path validation") {
        test("DefaultAudioLibrary.createFromFile throws InvalidAudioFilePathException when file does not exist") {
            Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                val nonExistent = fs.getPath("/nowhere.mp3")
                val library = DefaultAudioLibrary(VolatileRepository("AudioLibrary"))
                val ex = shouldThrow<InvalidAudioFilePathException> { library.createFromFile(nonExistent) }
                ex.message!! shouldContain "does not exist"
                ex.message!! shouldContain "/nowhere.mp3"
            }
        }

        test("DefaultAudioLibrary.createFromFile throws InvalidAudioFilePathException when path is a directory") {
            Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                val dir = fs.getPath("/a-directory")
                Files.createDirectory(dir)
                val library = DefaultAudioLibrary(VolatileRepository("AudioLibrary"))
                val ex = shouldThrow<InvalidAudioFilePathException> { library.createFromFile(dir) }
                ex.message!! shouldContain "is not a regular file"
            }
        }

        test("DefaultAudioLibrary.createFromFile throws InvalidAudioFilePathException when file is not readable")
            .config(enabled = !OsDetector.isWindows) {
                val tempFile = Files.createTempFile("unreadable", ".mp3")
                try {
                    // setReadable(false) is a no-op when running as root or on filesystems that don't
                    // honor POSIX permissions — skip rather than fail spuriously.
                    val demoted = tempFile.toFile().setReadable(false) && !tempFile.toFile().canRead()
                    if (!demoted) {
                        throw org.opentest4j.TestAbortedException(
                            "Cannot revoke read permission on this filesystem/user — skipping"
                        )
                    }
                    val library = DefaultAudioLibrary(VolatileRepository("AudioLibrary"))
                    val ex = shouldThrow<InvalidAudioFilePathException> { library.createFromFile(tempFile) }
                    ex.message!! shouldContain "is not readable"
                } finally {
                    tempFile.toFile().setReadable(true)
                    Files.deleteIfExists(tempFile)
                }
            }

        test("DefaultAudioLibrary.createFromFile InvalidAudioFilePathException is catchable") {
            Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                val nonExistent = fs.getPath("/nowhere.mp3")
                val library = DefaultAudioLibrary(VolatileRepository("AudioLibrary"))
                shouldThrow<InvalidAudioFilePathException> { library.createFromFile(nonExistent) }
            }
        }
    }

    test("AudioItemManipulationException has proper message and cause") {
        val testMessage = "Test error message"
        val testCause = RuntimeException("Test cause")

        val exceptionWithCause = AudioItemManipulationException(testMessage, testCause)
        exceptionWithCause.message shouldBe testMessage
        exceptionWithCause.cause shouldBe testCause
        exceptionWithCause.toString()

        val exceptionWithoutCause = AudioItemManipulationException(testMessage)
        exceptionWithoutCause.message shouldBe testMessage
        exceptionWithoutCause.cause shouldBe null
        exceptionWithoutCause.toString()
    }

    context("MutableAudioItem Windows path validation") {
        test("MutableAudioItem throws WindowsPathException for a reserved-name path when isWindows=true") {
            OsDetector.withOverriddenIsWindows(true) {
                // Use Jimfs windows configuration so the path's filesystem separator is `\`. The
                // validator only triggers for Windows-style paths now, because Unix-style paths
                // (e.g. Jimfs unix on a Windows host parsing `file:///C:/...` URIs) don't reach
                // the Win32 IO layer and use `:` legitimately. Jimfs windows rejects forbidden
                // chars at parse time, so the test uses a reserved name (parsable, then rejected
                // by the validator) to exercise the violation path.
                Jimfs.newFileSystem(Configuration.windows()).use { fs ->
                    val forbidden = fs.getPath("C:\\tmp\\NUL.mp3")
                    shouldThrow<WindowsPathException> { MutableAudioItem(forbidden, metadata = AudioItemMetadata()) }
                }
            }
        }

        test("MutableAudioItem pass-through on Linux for path with Windows-only forbidden chars") {
            OsDetector.withOverriddenIsWindows(false) {
                Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                    val path = fs.getPath("/music/bad|name.mp3")
                    // WindowsPathValidator is a no-op on Linux, so pipe char is fine and construction succeeds.
                    // Existence checks now live in DefaultAudioLibrary.createFromFile, not the constructor.
                    val item = MutableAudioItem(path, metadata = AudioItemMetadata())
                    item.path shouldBe path
                }
            }
        }

        test("MutableAudioItem pass-through for Unix-style path on Jimfs even when isWindows=true") {
            // Production scenario: deserializing a JSON entry whose path lives on Jimfs unix (e.g.
            // FX serializer tests) while running on a Windows host. The path uses Unix conventions,
            // never reaches the Win32 IO layer, and may contain `:` legitimately — validator skips it.
            OsDetector.withOverriddenIsWindows(true) {
                Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                    val path = fs.getPath("/work/C:/Users/runner/Temp/song.mp3")
                    val item = MutableAudioItem(path, metadata = AudioItemMetadata())
                    item.path shouldBe path
                }
            }
        }
    }
})