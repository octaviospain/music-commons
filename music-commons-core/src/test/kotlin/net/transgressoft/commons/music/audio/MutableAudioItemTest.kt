package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.FLAC
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.MP4_INFO
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import net.transgressoft.commons.music.audio.MutableAudioItemTestBridge.createAudioItem
import net.transgressoft.commons.music.common.OsDetector
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
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
            artist = ImmutableArtist.of("The Beatles", CountryCode.UK),
            album =
                ImmutableAlbum(
                    "Help!",
                    ImmutableArtist.of("The Beatles Band", CountryCode.UK), true, 1965, ImmutableLabel.of("EMI", CountryCode.US)
                ),
            bpm = 120f,
            trackNumber = 13,
            discNumber = 1,
            comments = "Best song ever!",
            genres = setOf(Genre.Rock),
            encoder = "transgressoft",
            playCount = 0
        ).next()

    context("Can be created, serialized to json, and and write changes to file metadata from") {
        withData(
            mapOf(
                "a mp3 file" to Arb.realAudioFile(ID3_V_24, expectedAttributes).next(),
                "a m4a file" to Arb.realAudioFile(MP4_INFO, expectedAttributes).next(),
                "a wav file" to Arb.realAudioFile(WAV, expectedAttributes).next(),
                "a flac file" to Arb.realAudioFile(FLAC, expectedAttributes).next()
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
                                ImmutableAlbum(
                                    "Help!",
                                    ImmutableArtist.of("The Beatles Band", CountryCode.UNDEFINED), true, 1965, ImmutableLabel.of("EMI", CountryCode.UNDEFINED)
                                )
                        }
                }

            // Test toString() and compareTo() for coverage
            audioItem.album.toString() shouldContain "ImmutableAlbum"
            audioItem.album.toString() shouldContain "Help!"
            audioItem.artist.toString() shouldContain "The Beatles"
            audioItem.album.compareTo(ImmutableAlbum.UNKNOWN) shouldNotBe 0
            audioItem.artist.compareTo(ImmutableArtist.UNKNOWN) shouldNotBe 0
            audioItem.album.label.compareTo(ImmutableLabel.UNKNOWN) shouldNotBe 0

            // Test AudioFileType extension and toString() for coverage
            val fileExtension = audioItem.path.extension
            audioItem.path.extension.toAudioFileType().toString() shouldBe fileExtension
            audioItem.path.extension.toAudioFileType().extension shouldBe fileExtension

            json.encodeToString(AudioItemSerializer, audioItem).let {
                it shouldEqualJson audioItem.asJsonValue()
                json.decodeFromString<MutableAudioItem>(it) shouldBe audioItem
            }

            val audioItemChanges = Arb.audioItemChange().next()
            audioItem.update(audioItemChanges)

            audioItem.writeMetadata().join()
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

    context("Has expected coverImage after deserialization") {
        val audioItem =
            createAudioItem(
                Arb.realAudioFile(ID3_V_24) {
                    coverImageBytes = null
                }.next()
            )

        audioItem.coverImageBytes shouldBe null

        audioItem.coverImageBytes = testCoverBytes

        audioItem.writeMetadata()

        eventually(200.milliseconds) {
            val encodedAudioItem = json.encodeToString(AudioItemSerializer, audioItem)
            val decodedAudioItem = json.decodeFromString<MutableAudioItem>(encodedAudioItem)

            decodedAudioItem.coverImageBytes shouldBe testCoverBytes
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

    context("MutableAudioItem three-check path validation") {
        test("MutableAudioItem throws InvalidAudioFilePathException when file does not exist") {
            Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                val nonExistent = fs.getPath("/nowhere.mp3")
                val ex = shouldThrow<InvalidAudioFilePathException> { MutableAudioItem(nonExistent) }
                ex.message!! shouldContain "does not exist"
                ex.message!! shouldContain "/nowhere.mp3"
            }
        }

        test("MutableAudioItem throws InvalidAudioFilePathException when path is a directory") {
            Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                val dir = fs.getPath("/a-directory")
                Files.createDirectory(dir)
                val ex = shouldThrow<InvalidAudioFilePathException> { MutableAudioItem(dir) }
                ex.message!! shouldContain "is not a regular file"
            }
        }

        test("MutableAudioItem throws InvalidAudioFilePathException when file is not readable")
            .config(enabled = !OsDetector.isWindows) {
                val tempFile = Files.createTempFile("unreadable", ".mp3")
                try {
                    tempFile.toFile().setReadable(false)
                    val ex = shouldThrow<InvalidAudioFilePathException> { MutableAudioItem(tempFile) }
                    ex.message!! shouldContain "is not readable"
                } finally {
                    tempFile.toFile().setReadable(true)
                    Files.deleteIfExists(tempFile)
                }
            }

        test("MutableAudioItem InvalidAudioFilePathException is catchable as AudioItemManipulationException") {
            Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                val nonExistent = fs.getPath("/nowhere.mp3")
                shouldThrow<AudioItemManipulationException> { MutableAudioItem(nonExistent) }
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
        test("MutableAudioItem throws WindowsPathException for a Windows-invalid path when isWindows=true") {
            OsDetector.withOverriddenIsWindows(true) {
                Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                    val forbidden = fs.getPath("/tmp/bad|name.mp3")
                    shouldThrow<WindowsPathException> { MutableAudioItem(forbidden) }
                }
            }
        }

        test("MutableAudioItem pass-through on Linux for path with Windows-only forbidden chars") {
            OsDetector.withOverriddenIsWindows(false) {
                Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                    val path = fs.getPath("/music/bad|name.mp3")
                    // WindowsPathValidator is a no-op on Linux, so pipe char is fine
                    // File does not exist, so it throws InvalidAudioFilePathException (not WindowsPathException)
                    val ex = shouldThrow<InvalidAudioFilePathException> { MutableAudioItem(path) }
                    (ex is WindowsPathException) shouldBe false
                }
            }
        }
    }
})