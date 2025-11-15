package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.FLAC
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.MP4_INFO
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
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
            genre = Genre.ROCK,
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
                MutableAudioItem(filePath).apply {
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
                            // the album artists' country code is not saved in the ID3 tag
                            album =
                                ImmutableAlbum(
                                    "Help!",
                                    ImmutableArtist.of("The Beatles Band", CountryCode.UNDEFINED), true, 1965, ImmutableLabel.of("EMI", CountryCode.US)
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
            val loadedAudioItem: AudioItem = MutableAudioItem(filePath, audioItem.id)

            assertSoftly {
                loadedAudioItem.id shouldBe audioItem.id
                loadedAudioItem.dateOfCreation shouldBeAfter audioItem.dateOfCreation
                loadedAudioItem.lastDateModified shouldBeAfter audioItem.lastDateModified
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
                loadedAudioItem.genre shouldBe audioItem.genre
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
            MutableAudioItem(
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
})