package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItemChange
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryFlacFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryM4aFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryMp3File
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryWavFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.testCoverBytes
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.Json

internal const val expectedTitle = "Yesterday"
internal const val expectedArtistName = "The Beatles"
internal val expectedLabel: Label = ImmutableLabel("EMI", CountryCode.US)
internal const val expectedAlbumName = "Help!"
internal const val expectedAlbumArtistName = "The Beatles Band"
internal const val expectedIsCompilation = true
internal const val expectedYear: Short = 1965
internal const val expectedBpm = 120f
internal const val expectedTrackNumber: Short = 13
internal const val expectedDiscNumber: Short = 1
internal const val expectedComments = "Best song ever!"
internal val expectedGenre = Genre.ROCK
internal const val expectedEncoder = "transgressoft"
internal val expectedDateOfCreation = LocalDateTime.now()
internal val expectedArtist = ImmutableArtist(expectedArtistName, CountryCode.UK)
internal val expectedAlbumArtist = ImmutableArtist(expectedAlbumArtistName, CountryCode.UK)
internal val expectedAlbum = ImmutableAlbum(expectedAlbumName, expectedAlbumArtist, expectedIsCompilation, expectedYear, expectedLabel)

internal class ImmutableAudioItemTest : FunSpec({

    val json = Json {
        serializersModule = audioItemSerializerModule
        prettyPrint = true
    }

    data class TestAudioFile(
        val file: File,
        val duration: Duration,
        val bitRate: Int,
        val encoding: String
    )

    fun File.createTestAudioFile(): TestAudioFile =
        AudioFileIO.read(this).let {
            TestAudioFile(
                this,
                Duration.ofSeconds(it.audioHeader.trackLength.toLong()),
                getBitRate(it.audioHeader),
                it.audioHeader.encodingType
            )
        }

    fun AudioItemTestAttributes.setExpectedAttributes() {
        title = expectedTitle
        artist = expectedArtist
        album = expectedAlbum
        bpm = expectedBpm
        trackNumber = expectedTrackNumber
        discNumber = expectedDiscNumber
        comments = expectedComments
        genre = expectedGenre
        encoder = expectedEncoder
        dateOfCreation = expectedDateOfCreation
    }

    context("should create an audio item, that is serializable to json, and write changes from/to a") {
        withData(
            mapOf(
                "mp3 file" to arbitraryMp3File(AudioItemTestAttributes::setExpectedAttributes).next().createTestAudioFile(),
                "m4a file" to arbitraryM4aFile(AudioItemTestAttributes::setExpectedAttributes).next().createTestAudioFile(),
                "wav file" to arbitraryWavFile(AudioItemTestAttributes::setExpectedAttributes).next().createTestAudioFile(),
                "flac file" to arbitraryFlacFile(AudioItemTestAttributes::setExpectedAttributes).next().createTestAudioFile()
            )
        ) { testAudioFile ->
            val audioItem = ImmutableAudioItem.createFromFile(testAudioFile.file.toPath()).also { audioItem ->
                audioItem.path shouldBe testAudioFile.file.toPath()
                audioItem.fileName shouldBe testAudioFile.file.toPath().fileName.toString()
                audioItem.length shouldBe testAudioFile.file.length()
                audioItem.extension shouldBe testAudioFile.file.extension
                audioItem.duration shouldBe testAudioFile.duration
                audioItem.bitRate shouldBe testAudioFile.bitRate
                audioItem.encoding shouldBe testAudioFile.encoding
                audioItem.should(::matchAudioItemProperties)
            }

            json.encodeToString(ImmutableAudioItem.serializer(), audioItem as ImmutableAudioItem).let {
                it shouldBe expectedJsonString(audioItem)
                json.decodeFromString<ImmutableAudioItem>(it) shouldBe audioItem
            }

            val audioItemChanges = arbitraryAudioItemChange.next()
            val updatedAudioItem = audioItem.update(audioItemChanges)

            updatedAudioItem.writeMetadata()

            val loadedAudioItem: AudioItem = ImmutableAudioItem.createFromFile(updatedAudioItem.path)
            assertSoftly {
                loadedAudioItem.id shouldBe updatedAudioItem.id
                loadedAudioItem.dateOfCreation shouldBeAfter updatedAudioItem.dateOfCreation
                loadedAudioItem.lastDateModified shouldBeAfter updatedAudioItem.lastDateModified
                loadedAudioItem.path shouldBe updatedAudioItem.path
                loadedAudioItem.fileName shouldBe updatedAudioItem.fileName
                loadedAudioItem.extension shouldBe updatedAudioItem.extension
                loadedAudioItem.title shouldBe updatedAudioItem.title
                loadedAudioItem.duration shouldBe updatedAudioItem.duration
                loadedAudioItem.bitRate shouldBe updatedAudioItem.bitRate
                loadedAudioItem.album.albumArtist.name shouldBe updatedAudioItem.album.albumArtist.name
                loadedAudioItem.album.albumArtist.countryCode shouldBe CountryCode.UNDEFINED    // album country code is not updated because there is no ID3 tag for it
                loadedAudioItem.album.isCompilation shouldBe updatedAudioItem.album.isCompilation
                loadedAudioItem.album.label.name shouldBe updatedAudioItem.album.label.name
                loadedAudioItem.album.label.countryCode shouldBe CountryCode.UNDEFINED  // label country code is not updated because there is no ID3 tag for it
                loadedAudioItem.artist.name shouldBe updatedAudioItem.artist.name
                loadedAudioItem.artist.countryCode shouldBe updatedAudioItem.artist.countryCode // artist country code is saved into COUNTRY ID3 tag
                if (testAudioFile.file.extension == "m4a") {
                    loadedAudioItem.bpm shouldBe updatedAudioItem.bpm?.toInt()?.toFloat()
                } else {
                    loadedAudioItem.bpm shouldBe updatedAudioItem.bpm
                }
                loadedAudioItem.trackNumber shouldBe updatedAudioItem.trackNumber
                loadedAudioItem.discNumber shouldBe updatedAudioItem.discNumber
                loadedAudioItem.comments shouldBe updatedAudioItem.comments
                loadedAudioItem.genre shouldBe updatedAudioItem.genre
                loadedAudioItem.encoding shouldBe updatedAudioItem.encoding
                loadedAudioItem.encoder shouldBe updatedAudioItem.encoder
                loadedAudioItem.coverImageBytes shouldBe updatedAudioItem.coverImageBytes
                loadedAudioItem.uniqueId shouldBe updatedAudioItem.uniqueId
                loadedAudioItem.toString() shouldBe updatedAudioItem.toString()
            }
        }
    }

    context("return coverImage after being deserialized") {
        val audioItem = ImmutableAudioItem.createFromFile(arbitraryMp3File { coverImageBytes = null }.next().toPath())
        audioItem.coverImageBytes shouldBe null

        val updatedAudioItem = audioItem.update { coverImageBytes = testCoverBytes }
        updatedAudioItem.coverImageBytes shouldBe testCoverBytes

        updatedAudioItem.writeMetadata()

        val encodedAudioItem = json.encodeToString(ImmutableAudioItem.serializer(), updatedAudioItem as ImmutableAudioItem)
        val decodedAudioItem = json.decodeFromString<ImmutableAudioItem>(encodedAudioItem)

        decodedAudioItem.coverImageBytes shouldBe testCoverBytes
    }
})

fun expectedJsonString(audioItem: AudioItem) =
    """
        {
            "id": ${audioItem.id},
            "path": "${audioItem.path}",
            "title": "${audioItem.title}",
            "duration": ${audioItem.duration.toSeconds()},
            "bitRate": ${audioItem.bitRate},
            "artist": {
                "type": "ImmutableArtist",
                "name": "${audioItem.artist.name}",
                "countryCode": "${audioItem.artist.countryCode}"
            },
            "album": {
                "type": "ImmutableAlbum",
                "name": "${audioItem.album.name}",
                "albumArtist": {
                    "type": "ImmutableArtist",
                    "name": "${audioItem.album.albumArtist.name}"
                },
                "isCompilation": ${audioItem.album.isCompilation},
                "year": ${audioItem.album.year},
                "label": {
                    "type": "ImmutableLabel",
                    "name": "${audioItem.album.label.name}"
                }
            },
            "genre": "${audioItem.genre.name}",
            "comments": "${audioItem.comments}",
            "trackNumber": ${audioItem.trackNumber},
            "discNumber": ${audioItem.discNumber},
            "bpm": ${audioItem.bpm},
            "encoder": "${audioItem.encoder}",
            "encoding": "${audioItem.encoding}",
            "dateOfCreation": ${audioItem.dateOfCreation.toEpochSecond(ZoneOffset.UTC)},
            "lastDateModified": ${audioItem.lastDateModified.toEpochSecond(ZoneOffset.UTC)}
        }""".trimIndent()

fun matchAudioItemProperties(audioItem: AudioItem) {
    audioItem.id shouldBe UNASSIGNED_ID
    audioItem.dateOfCreation shouldBeAfter expectedDateOfCreation
    audioItem.lastDateModified shouldBe audioItem.dateOfCreation
    audioItem.title shouldBe expectedTitle
    audioItem.album.name shouldBe expectedAlbumName
    audioItem.album.albumArtist.name shouldBe expectedAlbumArtistName
    audioItem.album.albumArtist.countryCode shouldBe CountryCode.UNDEFINED
    audioItem.album.label.name shouldBe expectedLabel.name
    audioItem.album.label.countryCode shouldBe CountryCode.UNDEFINED
    audioItem.artist shouldBe expectedArtist
    audioItem.bpm shouldBe expectedBpm
    audioItem.trackNumber shouldBe expectedTrackNumber
    audioItem.discNumber shouldBe expectedDiscNumber
    audioItem.comments shouldBe expectedComments
    audioItem.genre shouldBe expectedGenre
    audioItem.encoder shouldBe expectedEncoder
    audioItem.uniqueId shouldBe buildString {
        append(audioItem.path.fileName.toString().replace(' ', '_'))
        append('-')
        append(audioItem.title)
        append('-')
        append(audioItem.duration.toSeconds())
        append('-')
        append(audioItem.bitRate)
    }
    audioItem.artistsInvolved shouldContainExactly AudioUtils.getArtistsNamesInvolved(
        audioItem.title,
        audioItem.artist.name,
        audioItem.album.albumArtist.name
    )
}
