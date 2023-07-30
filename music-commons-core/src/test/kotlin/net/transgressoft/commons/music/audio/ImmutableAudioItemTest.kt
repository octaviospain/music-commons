package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.date.shouldBeBefore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import net.transgressoft.commons.music.audio.AudioItemTestUtil2.mp3File
import net.transgressoft.commons.music.audio.AudioItemTestUtil2.testCoverBytes
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * @author Octavio Calleya
 */
internal class ImmutableAudioItemTest : StringSpec({

    val id = 9
    val path = Path.of("testfiles", "testeable.mp3")
    val title = "Yesterday"
    val duration = Duration.ofMinutes(2)
    val bitRate = 320
    val artistName = "The Beatles"
    val label: Label = ImmutableLabel("EMI", CountryCode.US)
    val albumName = "Help!"
    val albumArtistName = "The Beatles Band"
    val isCompilation = false
    val year: Short = 1965
    val bpm = 120f
    val trackNumber: Short = 13
    val discNumber: Short = 1
    val comments = "Best song ever!"
    val genre = Genre.ROCK
    val encoding = "Lame MP3"
    val encoder = "transgressoft"
    val dateOfCreation = LocalDateTime.now()
    val artist = ImmutableArtist(artistName, CountryCode.UK)
    val albumArtist = ImmutableArtist(albumArtistName, CountryCode.UK)
    val album = ImmutableAlbum(albumName, albumArtist, isCompilation, year, label)

    var audioItem = ImmutableAudioItem(id, path, title, duration, bitRate, artist, album, genre, comments, trackNumber, discNumber, bpm, encoder, encoding, null, dateOfCreation)

    "AudioItem properties" {
        assertSoftly {
            audioItem.id shouldBe 9
            audioItem.lastDateModified shouldBe dateOfCreation
            audioItem.dateOfCreation shouldBe dateOfCreation
            audioItem.dateOfCreation shouldBeBefore LocalDateTime.now()
            audioItem.path shouldBe path
            audioItem.fileName shouldBe "testeable.mp3"
            audioItem.extension shouldBe "mp3"
            audioItem.title shouldBe title
            audioItem.duration shouldBe duration
            audioItem.bitRate shouldBe bitRate
            audioItem.album.name shouldBe album.name
            audioItem.album.albumArtist.name shouldBe album.albumArtist.name
            audioItem.album.albumArtist.countryCode shouldBe CountryCode.UK
            audioItem.album.isCompilation shouldBe false
            audioItem.album.year shouldBe album.year
            audioItem.album.label.name shouldBe album.label.name
            audioItem.album.label.countryCode shouldBe CountryCode.US
            audioItem.artist.name shouldBe artist.name
            audioItem.artist.countryCode shouldBe CountryCode.UK
            audioItem.bpm shouldBe bpm
            audioItem.trackNumber shouldBe trackNumber
            audioItem.discNumber shouldBe discNumber
            audioItem.comments shouldBe comments
            audioItem.genre shouldBe genre
            audioItem.encoding shouldBe encoding
            audioItem.encoder shouldBe encoder
            audioItem.uniqueId shouldBe "testeable.mp3-Yesterday-120-320"
            audioItem.toString() shouldBe "ImmutableAudioItem(id=9, path=testfiles/testeable.mp3, title=Yesterday, artist=The Beatles)"
        }

        val previousDateModified = audioItem.lastDateModified
        audioItem = audioItem.copy(comments = "modified", lastDateModified = LocalDateTime.now())
        audioItem.lastDateModified shouldBeAfter previousDateModified
        audioItem.comments shouldBe "modified"

        val newAlbum = mock<Album> {
            on { this@on.name } doReturn "OtherAlbum"
            on { this@on.albumArtist } doReturn ImmutableArtist("Other Artist")
            on { this@on.isCompilation } doReturn true
            on { this@on.year } doReturn 1999.toShort()
            on { this@on.label } doReturn ImmutableLabel.UNKNOWN
        }
        val audioItemWithModifiedAlbum = audioItem.copy(
            title = "Other title",
            album = newAlbum,
            encoder = "New encoder",
            encoding = "New encoding",
            bpm = 128f)

        audioItemWithModifiedAlbum shouldNotBe audioItem
        audioItemWithModifiedAlbum.album.albumArtist.name shouldBe "Other Artist"
        audioItemWithModifiedAlbum.title shouldBe "Other title"
        audioItemWithModifiedAlbum.album.isCompilation shouldBe true
        audioItemWithModifiedAlbum.encoder shouldBe "New encoder"
        audioItemWithModifiedAlbum.encoding shouldBe "New encoding"
        audioItemWithModifiedAlbum.bpm shouldBe 128f
        audioItem.length shouldBe 0

        val unknownAlbum = mock<Album> {
            on { this@on.name } doReturn ""
            on { this@on.albumArtist } doReturn ImmutableArtist.UNKNOWN
            on { this@on.isCompilation } doReturn true
            on { this@on.year } doReturn 1999.toShort()
            on { this@on.label } doReturn ImmutableLabel.UNKNOWN
        }
        var modifiedAudioItem = audioItemWithModifiedAlbum.copy(
            genre = Genre.UNDEFINED,
            album = unknownAlbum,
            comments = "Modified")

        modifiedAudioItem.artist shouldBe audioItem.artist
        modifiedAudioItem.album.name shouldBe ""
        modifiedAudioItem.genre shouldBe Genre.UNDEFINED
        modifiedAudioItem.comments shouldBe "Modified"
        modifiedAudioItem.artist shouldBe artist

        modifiedAudioItem = modifiedAudioItem.copy(
            artist = ImmutableArtist.UNKNOWN,
            path = Path.of("/moved/song.mp3"),
            discNumber = 2.toShort(),
            trackNumber = 3.toShort())
        modifiedAudioItem.path.toString() shouldBe "/moved/song.mp3"
        modifiedAudioItem.discNumber shouldBe 2.toShort()
        modifiedAudioItem.trackNumber shouldBe 3.toShort()
    }
    
    "AudioItem writes metadata into file" {
        val file = tempfile("audioItem-test", ".mp3").also { it.deleteOnExit() }
        mp3File.copyTo(file, overwrite = true)

        var updatedAudioItem: AudioItemBase = ImmutableAudioItem.createFromFile(file.toPath())
        val thisDateOfCreation = updatedAudioItem.dateOfCreation
        updatedAudioItem = updatedAudioItem.update(
            AudioItemMetadataChange(
                title,
                artist,
                albumName,
                albumArtist,
                isCompilation,
                year,
                label,
                testCoverBytes,
                genre,
                comments,
                trackNumber,
                discNumber,
                bpm
            )
        )

        assertSoftly {
            updatedAudioItem.id shouldBe 0
            updatedAudioItem.dateOfCreation shouldBe thisDateOfCreation
            updatedAudioItem.dateOfCreation shouldBeBefore LocalDateTime.now()
            updatedAudioItem.lastDateModified shouldBeAfter dateOfCreation
            updatedAudioItem.path shouldBe file.toPath()
            updatedAudioItem.fileName shouldContain "audioItem-test"
            updatedAudioItem.extension shouldBe "mp3"
            updatedAudioItem.title shouldBe title
            updatedAudioItem.duration shouldBe 8.seconds.toJavaDuration()
            updatedAudioItem.bitRate shouldBe 143
            updatedAudioItem.album.name shouldBe album.name
            updatedAudioItem.album.albumArtist.name shouldBe album.albumArtist.name
            updatedAudioItem.album.albumArtist.countryCode shouldBe CountryCode.UK
            updatedAudioItem.album.isCompilation shouldBe false
            updatedAudioItem.album.year shouldBe album.year
            updatedAudioItem.album.label.name shouldBe album.label.name
            updatedAudioItem.album.label.countryCode shouldBe CountryCode.US
            updatedAudioItem.artist.name shouldBe artist.name
            updatedAudioItem.artist.countryCode shouldBe CountryCode.UK
            updatedAudioItem.bpm shouldBe bpm
            updatedAudioItem.trackNumber shouldBe trackNumber
            updatedAudioItem.discNumber shouldBe discNumber
            updatedAudioItem.comments shouldBe comments
            updatedAudioItem.genre shouldBe genre
            updatedAudioItem.encoding shouldBe "MPEG-1 Layer 3"
            updatedAudioItem.encoder shouldBe ""
            updatedAudioItem.coverImage shouldBe testCoverBytes
            updatedAudioItem.uniqueId shouldBe "${updatedAudioItem.fileName}-Yesterday-8-143"
            updatedAudioItem.toString() shouldBe "ImmutableAudioItem(id=0, path=${file.toPath()}, title=Yesterday, artist=The Beatles)"
        }

        updatedAudioItem.writeMetadata()

        eventually(2.seconds) {
            val loadedAudioItem = ImmutableAudioItem.createFromFile(updatedAudioItem.path)
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
                loadedAudioItem.bpm shouldBe updatedAudioItem.bpm
                loadedAudioItem.trackNumber shouldBe updatedAudioItem.trackNumber
                loadedAudioItem.discNumber shouldBe updatedAudioItem.discNumber
                loadedAudioItem.comments shouldBe updatedAudioItem.comments
                loadedAudioItem.genre shouldBe updatedAudioItem.genre
                loadedAudioItem.encoding shouldBe updatedAudioItem.encoding
                loadedAudioItem.encoder shouldBe updatedAudioItem.encoder
                loadedAudioItem.coverImage shouldBe updatedAudioItem.coverImage
                loadedAudioItem.uniqueId shouldBe updatedAudioItem.uniqueId
                loadedAudioItem.toString() shouldBe updatedAudioItem.toString()
            }
        }
    }

    "AudioItem returns coverImage after being deserialized" {
        val tempFile = tempfile("audioItem-test", ".mp3").also { it.deleteOnExit() }
        mp3File.copyTo(tempFile, overwrite = true)
        audioItem = audioItem.copy(path = tempFile.toPath())

        audioItem.coverImage shouldBe null

        val updatedAudioItem = audioItem.update { coverImage = testCoverBytes }
        updatedAudioItem.writeMetadata()

        val json = Json { serializersModule = audioItemSerializerModule; prettyPrint = true }
        val encodedAudioItem = json.encodeToString(AudioItemBase.serializer(), audioItem)
        encodedAudioItem shouldBe """
        {
            "audioItemType": "DefaultAudioItem",
            "id": ${audioItem.id},
            "path": "${audioItem.path}",
            "title": "${audioItem.title}",
            "duration": ${audioItem.duration.toSeconds()},
            "bitRate": ${audioItem.bitRate},
            "artist": {
                "type": "DefaultArtist",
                "name": "${audioItem.artist.name}",
                "countryCode": "${audioItem.artist.countryCode}"
            },
            "album": {
                "type": "DefaultAlbum",
                "name": "${audioItem.album.name}",
                "albumArtist": {
                    "type": "DefaultArtist",
                    "name": "${audioItem.album.albumArtist.name}",
                    "countryCode": "${audioItem.artist.countryCode}"
                },
                "year": ${audioItem.album.year},
                "label": {
                    "type": "DefaultLabel",
                    "name": "${audioItem.album.label.name}",
                    "countryCode": "${audioItem.album.label.countryCode}"
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

        val decodedAudioItem = json.decodeFromString<AudioItemBase>(encodedAudioItem)

        decodedAudioItem.coverImage shouldBe testCoverBytes
    }
})