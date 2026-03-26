package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import com.neovisionaries.i18n.CountryCode
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tests for [AudioItemSerializer] covering JSON encoding structure and round-trip
 * serialization fidelity.
 */
@DisplayName("AudioItemSerializer")
internal class AudioItemSerializerTest : StringSpec({

    val json =
        Json {
            prettyPrint = false
            explicitNulls = true
        }

    "AudioItemSerializer encodes golden JSON fixture with required fields for path, id, title, duration, artist and album" {
        val artist = ImmutableArtist.of("The Beatles", CountryCode.GB)
        val label = ImmutableLabel.of("EMI")
        val album = ImmutableAlbum("Abbey Road", artist, false, 1969.toShort(), label)

        val path =
            Arb.virtualAudioFile {
                this.title = "Come Together"
                this.artist = artist
                this.album = album
                this.genre = Genre.ROCK
                this.comments = "Classic track"
                this.trackNumber = 1
                this.discNumber = 1
            }.next()

        val audioItem: AudioItem = MutableAudioItemTestBridge.createAudioItem(path, 42)

        val encoded = json.encodeToString(AudioItemMapSerializer, mapOf(42 to audioItem))

        encoded shouldContain "\"path\""
        encoded shouldContain "\"id\""
        encoded shouldContain "\"title\""
        encoded shouldContain "Come Together"
        encoded shouldContain "\"duration\""
        encoded shouldContain "\"artist\""
        encoded shouldContain "The Beatles"
        encoded shouldContain "\"album\""
        encoded shouldContain "Abbey Road"
        encoded shouldContain "\"genre\""
        encoded shouldContain "\"playCount\""
    }

    "AudioItemSerializer round-trip serialization produces equal entity fields" {
        val artist = ImmutableArtist.of("Test Artist", CountryCode.US)
        val label = ImmutableLabel.of("Test Label")
        val album = ImmutableAlbum("Test Album", artist, true, 2010.toShort(), label)

        val path =
            Arb.virtualAudioFile {
                this.title = "Round Trip Song"
                this.artist = artist
                this.album = album
                this.genre = Genre.ROCK
                this.comments = "Test comment"
                this.trackNumber = 3
                this.discNumber = 2
                this.bpm = 120.0f
            }.next()

        val originalItem: AudioItem = MutableAudioItemTestBridge.createAudioItem(path, 99)

        val encoded = json.encodeToString(AudioItemMapSerializer, mapOf(originalItem.id to originalItem))
        val decoded = json.decodeFromString(AudioItemMapSerializer, encoded)

        val decodedItem = decoded.getValue(99)

        decodedItem.id shouldBe originalItem.id
        decodedItem.title shouldBe originalItem.title
        decodedItem.duration shouldBe originalItem.duration
        decodedItem.bitRate shouldBe originalItem.bitRate
        decodedItem.artist.name shouldBe originalItem.artist.name
        decodedItem.artist.countryCode shouldBe originalItem.artist.countryCode
        decodedItem.album.name shouldBe originalItem.album.name
        decodedItem.album.albumArtist.name shouldBe originalItem.album.albumArtist.name
        decodedItem.album.isCompilation shouldBe originalItem.album.isCompilation
        decodedItem.album.year shouldBe originalItem.album.year
        decodedItem.album.label.name shouldBe originalItem.album.label.name
        decodedItem.genre shouldBe originalItem.genre
        decodedItem.comments shouldBe originalItem.comments
        decodedItem.trackNumber shouldBe originalItem.trackNumber
        decodedItem.discNumber shouldBe originalItem.discNumber
        decodedItem.bpm shouldBe originalItem.bpm
        decodedItem.encoder shouldBe originalItem.encoder
        decodedItem.encoding shouldBe originalItem.encoding
        decodedItem.playCount shouldBe originalItem.playCount
    }

    "AudioItemSerializer encodes nested artist object with name and countryCode" {
        val artist = ImmutableArtist.of("Solo Artist", CountryCode.DE)

        val path =
            Arb.virtualAudioFile {
                this.artist = artist
            }.next()

        val audioItem: AudioItem = MutableAudioItemTestBridge.createAudioItem(path, 10)

        val encoded = json.encodeToString(AudioItemMapSerializer, mapOf(10 to audioItem))
        val jsonElement = Json.parseToJsonElement(encoded).jsonObject["10"]!!.jsonObject

        jsonElement["artist"]!!.jsonObject["name"]!!.jsonPrimitive.content shouldBe "Solo Artist"
        jsonElement["artist"]!!.jsonObject["countryCode"]!!.jsonPrimitive.content shouldBe "DE"
    }
})