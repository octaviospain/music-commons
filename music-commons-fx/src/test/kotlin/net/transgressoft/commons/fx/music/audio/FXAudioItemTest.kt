package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.ImmutableLabel
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.music.audio.audioItemChange
import net.transgressoft.commons.music.audio.testCoverBytes
import net.transgressoft.commons.music.audio.update
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import javafx.scene.image.Image
import java.io.ByteArrayInputStream
import java.util.Optional
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.Json

internal class FXAudioItemTest : StringSpec({

    val json =
        Json {
            serializersModule = observableAudioItemSerializerModule
            prettyPrint = true
        }

    "Changes its properties when observable properties are updated" {
        val path = Arb.virtualAudioFile().next()

        val fxAudioItem = FXAudioItem(path)
        assertSoftly {
            fxAudioItem.titleProperty.value shouldBe fxAudioItem.title
            fxAudioItem.artistProperty.value shouldBe fxAudioItem.artist
            fxAudioItem.albumProperty.value shouldBe fxAudioItem.album
            fxAudioItem.genre shouldBe fxAudioItem.genre
            fxAudioItem.comments shouldBe fxAudioItem.comments
            fxAudioItem.trackNumber shouldBe fxAudioItem.trackNumber
            fxAudioItem.discNumber shouldBe fxAudioItem.discNumber
            fxAudioItem.bpm shouldBe fxAudioItem.bpm
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
            fxAudioItem.lastDateModified shouldBeAfter lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldBeAfter lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.artistProperty.set(ImmutableArtist.of("Bon Jovi"))
        eventually(100.milliseconds) {
            fxAudioItem.artist.name shouldBe "Bon Jovi"
            fxAudioItem.artistsInvolved shouldContain ImmutableArtist.of("Bon Jovi")
            fxAudioItem.lastDateModified shouldBeAfter lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldBeAfter lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.albumProperty.set(ImmutableAlbum("New Album", ImmutableArtist.of("Bon Jovi"), false, 2021, ImmutableLabel.UNKNOWN))
        eventually(100.milliseconds) {
            fxAudioItem.album.name shouldBe "New Album"
            fxAudioItem.album.albumArtist.name shouldBe "Bon Jovi"
            fxAudioItem.lastDateModified shouldBeAfter lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldBeAfter lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        val newGenre = fxAudioItem.genre.randomDifferent()
        fxAudioItem.genre = newGenre
        eventually(100.milliseconds) {
            fxAudioItem.genreProperty.value shouldBe newGenre
            fxAudioItem.lastDateModified shouldBeAfter lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldBeAfter lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.comments = "New comments"
        eventually(100.milliseconds) {
            fxAudioItem.commentsProperty.value shouldBe "New comments"
            fxAudioItem.lastDateModified shouldBeAfter lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldBeAfter lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.trackNumber = 5
        eventually(100.milliseconds) {
            fxAudioItem.trackNumberProperty.value shouldBe 5
            fxAudioItem.lastDateModified shouldBeAfter lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldBeAfter lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.discNumber = 2
        eventually(100.milliseconds) {
            fxAudioItem.discNumberProperty.value shouldBe 2
            fxAudioItem.lastDateModified shouldBeAfter lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldBeAfter lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.bpm = 130f
        eventually(100.milliseconds) {
            fxAudioItem.bpmProperty.value shouldBe 130f
            fxAudioItem.lastDateModified shouldBeAfter lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldBeAfter lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.coverImageBytes = null
        eventually(100.milliseconds) {
            fxAudioItem.coverImageBytes shouldBe null
            fxAudioItem.coverImageProperty.value shouldBe Optional.empty()
            fxAudioItem.lastDateModified shouldBeAfter lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldBeAfter lastDateUpdated
        }

        lastDateUpdated = fxAudioItem.lastDateModified
        fxAudioItem.incrementPlayCount()
        eventually(500.milliseconds) {
            fxAudioItem.playCount shouldBe 1
            fxAudioItem.playCountProperty.value shouldBe 1
            fxAudioItem.lastDateModified shouldBeAfter lastDateUpdated
            fxAudioItem.lastDateModifiedProperty.value shouldBeAfter lastDateUpdated
        }
    }

    "Creates an audio item, that is serializable to json, and write changes to metadata" {
        val testAudioFile = Arb.realAudioFile().next()
        val fxAudioItem = FXAudioItem(testAudioFile)

        json.encodeToString(ObservableAudioItemSerializer, fxAudioItem).let {
            it.shouldEqualJson(fxAudioItem.asJsonValue())
            json.decodeFromString<FXAudioItem>(it) shouldBe fxAudioItem
        }

        val audioItemChanges = Arb.audioItemChange().next()
        fxAudioItem.update(audioItemChanges)

        fxAudioItem.writeMetadata().join()

        val loadedAudioItem = FXAudioItem(testAudioFile, fxAudioItem.id)
        assertSoftly {
            loadedAudioItem.id shouldBe fxAudioItem.id
            loadedAudioItem.dateOfCreation shouldBeAfter fxAudioItem.dateOfCreation
            loadedAudioItem.lastDateModified shouldBeAfter fxAudioItem.lastDateModified
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
            loadedAudioItem.genre shouldBe fxAudioItem.genre
            loadedAudioItem.comments shouldBe fxAudioItem.comments
            loadedAudioItem.trackNumber shouldBe fxAudioItem.trackNumber
            loadedAudioItem.discNumber shouldBe fxAudioItem.discNumber
            loadedAudioItem.bpm shouldBe fxAudioItem.bpm
            loadedAudioItem.coverImageBytes shouldBe fxAudioItem.coverImageBytes
            loadedAudioItem.playCount shouldBe fxAudioItem.playCount
            loadedAudioItem.uniqueId shouldBe fxAudioItem.uniqueId
            loadedAudioItem.toString() shouldBe fxAudioItem.toString()
        }
    }

    "Returns coverImage after deserialization" {
        val fxAudioItem = FXAudioItem(Arb.realAudioFile { coverImageBytes = null }.next())

        fxAudioItem.coverImageBytes = testCoverBytes

        fxAudioItem.writeMetadata().join()

        val encodedAudioItem = json.encodeToString(ObservableAudioItemSerializer, fxAudioItem)
        val decodedAudioItem = json.decodeFromString<FXAudioItem>(encodedAudioItem)

        decodedAudioItem.coverImageBytes shouldBe testCoverBytes
        decodedAudioItem.coverImageProperty.value shouldBePresent {
            it.height shouldBe Image(ByteArrayInputStream(decodedAudioItem.coverImageBytes)).height
        }
    }

    "FXAudioItem getter returns defensive copy — mutating returned array does not affect internal state" {
        val fxAudioItem = FXAudioItem(Arb.realAudioFile().next())
        fxAudioItem.coverImageBytes = testCoverBytes

        val returned = fxAudioItem.coverImageBytes!!
        val originalContent = returned.copyOf()
        returned[0] = 0x00.toByte()

        fxAudioItem.coverImageBytes!! shouldBe originalContent
    }

    "FXAudioItem setter stores defensive copy — mutating source array after set does not affect internal state" {
        val fxAudioItem = FXAudioItem(Arb.realAudioFile().next())
        val source = byteArrayOf(1, 2, 3, 4, 5)
        fxAudioItem.coverImageBytes = source
        source[0] = 99.toByte()

        fxAudioItem.coverImageBytes!![0] shouldBe 1.toByte()
    }
})

fun Genre.randomDifferent(): Genre {
    val values = enumValues<Genre>().filter { it != this }
    return values[Random.nextInt(values.size)]
}