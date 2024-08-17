package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItemChange
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryFlacFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryM4aFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryMp3File
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryWavFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.asJsonValue
import net.transgressoft.commons.music.audio.AudioItemTestUtil.testCoverBytes
import net.transgressoft.commons.music.audio.AudioItemTestUtil.update
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.exhaustive
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import java.time.Duration
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds
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

internal class MutableAudioItemTest : FunSpec({

    val json = Json {
        serializersModule = audioItemSerializerModule
        prettyPrint = true
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

    val list = listOf(
        arbitraryMp3File(AudioItemTestAttributes::setExpectedAttributes).next(),
        arbitraryM4aFile(AudioItemTestAttributes::setExpectedAttributes).next(),
        arbitraryWavFile(AudioItemTestAttributes::setExpectedAttributes).next(),
        arbitraryFlacFile(AudioItemTestAttributes::setExpectedAttributes).next()
    ).exhaustive()

    context("should create an audio item, that is serializable to json, and write changes to metadata") {
        checkAll(list) { testAudioFile ->
            lateinit var expectedDuration: Duration
            lateinit var expectedEncoding: String
            var expectedBitRate: Int
            AudioFileIO.read(testAudioFile).let {
                expectedDuration = Duration.ofSeconds(it.audioHeader.trackLength.toLong())
                expectedEncoding = it.audioHeader.encodingType
                expectedBitRate = getExpectedBitRate(it.audioHeader)
            }

            val audioItem = MutableAudioItem(testAudioFile.toPath()).also { audioItem ->
                audioItem.path shouldBe testAudioFile.toPath()
                audioItem.fileName shouldBe testAudioFile.toPath().fileName.toString()
                audioItem.length shouldBe testAudioFile.length()
                audioItem.extension shouldBe testAudioFile.extension
                audioItem.duration shouldBe expectedDuration
                audioItem.bitRate shouldBe expectedBitRate
                audioItem.encoding shouldBe expectedEncoding
                audioItem.playCount shouldBe 0
                audioItem.should(::matchAudioItemProperties)
            }

            json.encodeToString(AudioItemSerializer, audioItem).let {
                it.shouldEqualJson(audioItem.asJsonValue())
                json.decodeFromString<MutableAudioItem>(it) shouldBe audioItem
            }

            val audioItemChanges = arbitraryAudioItemChange.next()
            audioItem.update(audioItemChanges)

            audioItem.writeMetadata().join()
            val loadedAudioItem: AudioItem = MutableAudioItem(testAudioFile.toPath(), audioItem.id)

            eventually(100.milliseconds) {
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
                    loadedAudioItem.album.albumArtist.name shouldBe audioItem.album.albumArtist.name
                    loadedAudioItem.album.albumArtist.countryCode shouldBe CountryCode.UNDEFINED    // album country code is not updated because there is no ID3 tag for it
                    loadedAudioItem.album.isCompilation shouldBe audioItem.album.isCompilation
                    loadedAudioItem.album.label.name shouldBe audioItem.album.label.name
                    loadedAudioItem.album.label.countryCode shouldBe CountryCode.UNDEFINED  // label country code is not updated because there is no ID3 tag for it
                    loadedAudioItem.artist.name shouldBe audioItem.artist.name
                    loadedAudioItem.artist.countryCode shouldBe audioItem.artist.countryCode // artist country code is saved into COUNTRY ID3 tag
                    if (testAudioFile.extension == "m4a") {
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
    }

    context("should return coverImage after being deserialized") {
        val audioItem = MutableAudioItem(arbitraryMp3File { coverImageBytes = null }.next().toPath())
        audioItem.coverImageBytes shouldBe null

        audioItem.coverImageBytes = testCoverBytes

        audioItem.writeMetadata()

        eventually(100.milliseconds) {
            val encodedAudioItem = json.encodeToString(AudioItemSerializer, audioItem)
            val decodedAudioItem = json.decodeFromString<MutableAudioItem>(encodedAudioItem)

            decodedAudioItem.coverImageBytes shouldBe testCoverBytes
        }
    }
})

private fun getExpectedBitRate(audioHeader: AudioHeader): Int {
    val bitRate = audioHeader.bitRate
    return if ("~" == bitRate.substring(0, 1)) {
        bitRate.substring(1).toInt()
    } else {
        bitRate.toInt()
    }
}

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
