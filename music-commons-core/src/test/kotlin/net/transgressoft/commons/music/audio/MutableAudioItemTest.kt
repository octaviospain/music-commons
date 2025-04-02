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

internal const val EXPECTED_TITLE = "Yesterday"
internal const val EXPECTED_ARTIST_NAME = "The Beatles"
internal val expectedLabel: Label = ImmutableLabel("EMI", CountryCode.US)
internal const val EXPECTED_ALBUM_NAME = "Help!"
internal const val EXPECTED_ALBUM_ARTIST_NAME = "The Beatles Band"
internal const val EXPECTED_IS_COMPILATION = true
internal const val EXPECTED_YEAR: Short = 1965
internal const val EXPECTED_BPM = 120f
internal const val EXPECTED_TRACK_NUMBER: Short = 13
internal const val EXPECTED_DISC_NUMBER: Short = 1
internal const val EXPECTED_COMMENTS = "Best song ever!"
internal val expectedGenre = Genre.ROCK
internal const val EXPECTED_ENCODER = "transgressoft"
internal val expectedDateOfCreation = LocalDateTime.now()
internal val expectedArtist = ImmutableArtist(EXPECTED_ARTIST_NAME, CountryCode.UK)
internal val expectedAlbumArtist = ImmutableArtist(EXPECTED_ALBUM_ARTIST_NAME, CountryCode.UK)
internal val expectedAlbum = ImmutableAlbum(EXPECTED_ALBUM_NAME, expectedAlbumArtist, EXPECTED_IS_COMPILATION, EXPECTED_YEAR, expectedLabel)

internal class MutableAudioItemTest : FunSpec({

    val json =
        Json {
            serializersModule = audioItemSerializerModule
            prettyPrint = true
            explicitNulls = true
            allowStructuredMapKeys = true
        }

    fun AudioItemTestAttributes.setExpectedAttributes() {
        title = EXPECTED_TITLE
        artist = expectedArtist
        album = expectedAlbum
        bpm = EXPECTED_BPM
        trackNumber = EXPECTED_TRACK_NUMBER
        discNumber = EXPECTED_DISC_NUMBER
        comments = EXPECTED_COMMENTS
        genre = expectedGenre
        encoder = EXPECTED_ENCODER
        dateOfCreation = expectedDateOfCreation
    }

    val list =
        listOf(
            arbitraryMp3File(AudioItemTestAttributes::setExpectedAttributes).next(),
            arbitraryM4aFile(AudioItemTestAttributes::setExpectedAttributes).next(),
            arbitraryWavFile(AudioItemTestAttributes::setExpectedAttributes).next(),
            arbitraryFlacFile(AudioItemTestAttributes::setExpectedAttributes).next()
        ).exhaustive()

    context("MutableAudioItem creates and serializes and AudioItem to json and write changes to file metadata") {
        checkAll(list) { testAudioFile ->
            lateinit var expectedDuration: Duration
            lateinit var expectedEncoding: String
            var expectedBitRate: Int
            AudioFileIO.read(testAudioFile).let {
                expectedDuration = Duration.ofSeconds(it.audioHeader.trackLength.toLong())
                expectedEncoding = it.audioHeader.encodingType
                expectedBitRate = getExpectedBitRate(it.audioHeader)
            }

            val audioItem =
                MutableAudioItem(testAudioFile.toPath()).also { audioItem ->
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

    context("MutableAudioItem returns coverImage after being deserialized") {
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
    audioItem.title shouldBe EXPECTED_TITLE
    audioItem.album.name shouldBe EXPECTED_ALBUM_NAME
    audioItem.album.albumArtist.name shouldBe EXPECTED_ALBUM_ARTIST_NAME
    audioItem.album.albumArtist.countryCode shouldBe CountryCode.UNDEFINED
    audioItem.album.label.name shouldBe expectedLabel.name
    audioItem.album.label.countryCode shouldBe CountryCode.UNDEFINED
    audioItem.artist shouldBe expectedArtist
    audioItem.bpm shouldBe EXPECTED_BPM
    audioItem.trackNumber shouldBe EXPECTED_TRACK_NUMBER
    audioItem.discNumber shouldBe EXPECTED_DISC_NUMBER
    audioItem.comments shouldBe EXPECTED_COMMENTS
    audioItem.genre shouldBe expectedGenre
    audioItem.encoder shouldBe EXPECTED_ENCODER
    audioItem.uniqueId shouldBe
        buildString {
            append(audioItem.path.fileName.toString().replace(' ', '_'))
            append('-')
            append(audioItem.title)
            append('-')
            append(audioItem.duration.toSeconds())
            append('-')
            append(audioItem.bitRate)
        }
    audioItem.artistsInvolved shouldContainExactly
        AudioUtils.getArtistsNamesInvolved(
            audioItem.title, audioItem.artist.name, audioItem.album.albumArtist.name
        )
}