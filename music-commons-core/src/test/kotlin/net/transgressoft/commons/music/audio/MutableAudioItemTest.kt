package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.FLAC
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.MP4_INFO
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import net.transgressoft.commons.music.audio.JAudioTaggerMetadataIO
import net.transgressoft.commons.music.audio.MutableAudioItemTestBridge.createAudioItem
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
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
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.date.shouldNotBeBefore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
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
                AlbumDetails(
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
                                AlbumDetails(
                                    "Help!",
                                    Artist.of("The Beatles Band", CountryCode.UNDEFINED), true, 1965, Label.of("EMI", CountryCode.UNDEFINED)
                                )
                        }
                }

            // Test toString() and compareTo() for coverage
            audioItem.album.toString() shouldContain "Album"
            audioItem.album.toString() shouldContain "Help!"
            audioItem.artist.toString() shouldContain "The Beatles"
            audioItem.album.compareTo(AlbumDetails.UNKNOWN) shouldNotBe 0
            audioItem.artist.compareTo(Artist.UNKNOWN) shouldNotBe 0
            audioItem.album.label.compareTo(Label.UNKNOWN) shouldNotBe 0

            // Test AudioFileType extension and toString() for coverage
            val fileExtension = audioItem.path.extension
            audioItem.path.extension.toAudioFileType().toString() shouldBe fileExtension
            audioItem.path.extension.toAudioFileType().extension shouldBe fileExtension

            json.encodeToString(AudioItemMapSerializer, mapOf(audioItem.id to audioItem)).let {
                (json.decodeFromString(AudioItemMapSerializer, it).getValue(audioItem.id) as MutableAudioItem) shouldBe audioItem
            }

            // Deterministic, round-trip-safe values. The write→read path normalizes some fields
            // (e.g. artist names are title-cased on read, custom genres are reformatted), so fuzzed
            // random text does not survive faithfully; realistic canonical values verify the
            // persistence contract without depending on tag-library normalization quirks.
            val audioItemChanges =
                AudioItemChange(audioItem.id).apply {
                    title = "Paint It Black"
                    artist = Artist.of("The Rolling Stones", CountryCode.UK)
                    album = AlbumDetails("Aftermath", Artist.of("The Rolling Stones", CountryCode.UK), false, 1966, Label.of("Decca", CountryCode.UK))
                    genres = setOf(Rock)
                    comments = "Remastered stereo mix"
                    trackNumber = 5
                    discNumber = 1
                    bpm = 160f
                    playCount = 12
                }
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
            val encodedAudioItem = json.encodeToString(AudioItemMapSerializer, mapOf(audioItem.id to audioItem))
            val decodedAudioItem =
                json.decodeFromString(AudioItemMapSerializer, encodedAudioItem).getValue(audioItem.id) as MutableAudioItem

            decodedAudioItem.coverImageBytes shouldBe null
        }
    }

    context("MutableAudioItem coverImageBytes immutable contract") {
        test("MutableAudioItem getter returns the same reference that was set") {
            val audioItem = createAudioItem(Arb.realAudioFile(ID3_V_24).next())
            audioItem.coverImageBytes = testCoverBytes
            audioItem.coverImageBytes shouldBeSameInstanceAs testCoverBytes
        }

        test("MutableAudioItem setter stores the provided reference directly") {
            val audioItem = createAudioItem(Arb.realAudioFile(ID3_V_24).next())
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            audioItem.coverImageBytes = bytes
            audioItem.coverImageBytes shouldBeSameInstanceAs bytes
        }
    }

    context("MutableAudioItem.coverImageBytes lazy-load via metadataIO back-ref") {
        test("MutableAudioItem.coverImageBytes returns null for orphan item with no metadataIO wired") {
            val path = Arb.realAudioFile(ID3_V_24).next()
            val item = MutableAudioItem(path, 1, AudioItemMetadata())
            item.coverImageBytes shouldBe null
        }

        test("MutableAudioItem.coverImageBytes lazy-loads via metadataIO back-ref on first read and caches result") {
            val path = Arb.realAudioFile(ID3_V_24).next()
            val item = MutableAudioItem(path, 2, AudioItemMetadata())
            val metadataIO = JAudioTaggerMetadataIO()
            val expected = metadataIO.loadCover(path)
            item.metadataIO = metadataIO

            val firstRead = item.coverImageBytes
            firstRead shouldBe expected

            val secondRead = item.coverImageBytes
            secondRead shouldBe expected
        }
    }

    context("MutableAudioItem zero-retention at import") {
        test("DefaultAudioLibrary.createFromFile does not trigger cover load at import time") {
            val path = Arb.realAudioFile(ID3_V_24).next()
            var loadCoverCallCount = 0
            val spyMetadataIO =
                object : AudioMetadataIO {
                    val delegate = JAudioTaggerMetadataIO()

                    override fun readMetadata(path: Path) = delegate.readMetadata(path)

                    override fun loadCover(path: Path): ByteArray? {
                        loadCoverCallCount++
                        return delegate.loadCover(path)
                    }

                    override fun writeMetadata(item: ReactiveAudioItem<*>) = delegate.writeMetadata(item)
                }
            val library = DefaultAudioLibrary(VolatileRepository("AudioLibrary"), spyMetadataIO)
            library.createFromFile(path)

            loadCoverCallCount shouldBe 0
        }
    }

    context("MutableAudioItem clone shares cover reference") {
        test("MutableAudioItem clone cover bytes are same instance as original") {
            val audioItem = createAudioItem(Arb.realAudioFile(ID3_V_24).next())
            audioItem.coverImageBytes = testCoverBytes
            val clone = audioItem.clone()
            clone.coverImageBytes shouldBeSameInstanceAs audioItem.coverImageBytes
        }
    }

    context("MutableAudioItem.bpm rejects non-finite Float values") {
        // The ctor row is a distinct code path (AudioItemMetadata init) from the property setter rows.
        withData(
            nameFn = { (label, _, viaCtor) -> "rejects bpm=$label via ${if (viaCtor) "ctor" else "setter"}" },
            Triple("NaN", Float.NaN, false),
            Triple("POSITIVE_INFINITY", Float.POSITIVE_INFINITY, false),
            Triple("NaN", Float.NaN, true)
        ) { (_, value, viaCtor) ->
            shouldThrow<IllegalArgumentException> {
                if (viaCtor) {
                    AudioItemMetadata(bpm = value)
                } else {
                    createAudioItem(Arb.realAudioFile(ID3_V_24).next()).bpm = value
                }
            }
        }
    }

    context("DefaultAudioLibrary.createFromFile three-check path validation") {
        withData(
            nameFn = { (name, _) -> "DefaultAudioLibrary.createFromFile rejects $name" },
            "a non-existent file" to
                PathValidationCase({ fs -> fs.getPath("/nowhere.mp3") }, listOf("does not exist", "/nowhere.mp3")),
            "a directory" to
                PathValidationCase({ fs -> fs.getPath("/a-directory").also { Files.createDirectory(it) } }, listOf("is not a regular file"))
        ) { (_, case) ->
            Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                val path = case.pathSetup(fs)
                val library = DefaultAudioLibrary(VolatileRepository("AudioLibrary"))
                val ex = shouldThrow<InvalidAudioFilePathException> { library.createFromFile(path) }
                assertSoftly {
                    case.expectedMessageFragments.forEach { ex.message!! shouldContain it }
                }
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

private class PathValidationCase(
    val pathSetup: (FileSystem) -> Path,
    val expectedMessageFragments: List<String>
)