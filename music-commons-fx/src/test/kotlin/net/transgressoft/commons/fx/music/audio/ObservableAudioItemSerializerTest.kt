package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.AudioItemTestFactory
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.serialization.json.Json

/**
 * Tests for [ObservableAudioItemSerializer] verifying golden JSON structure and round-trip fidelity.
 */
internal class ObservableAudioItemSerializerTest : StringSpec({

    val json =
        Json {
            serializersModule = observableAudioItemSerializerModule
        }

    "ObservableAudioItemSerializer encodes all required JSON fields" {
        val path = Arb.virtualAudioFile().next()
        val fxAudioItem = FXAudioItem(path, AudioItemTestFactory.nextTestId())

        val encoded = json.encodeToString(ObservableAudioItemSerializer, fxAudioItem)

        encoded shouldContainJsonKey "path"
        encoded shouldContainJsonKey "id"
        encoded shouldContainJsonKey "title"
        encoded shouldContainJsonKey "duration"
        encoded shouldContainJsonKey "bitRate"
        encoded shouldContainJsonKey "artist"
        encoded shouldContainJsonKey "album"
        encoded shouldContainJsonKey "genre"
        encoded shouldContainJsonKey "dateOfCreation"
        encoded shouldContainJsonKey "lastDateModified"
        encoded shouldContainJsonKey "playCount"
    }

    "ObservableAudioItemSerializer round-trip preserves all fields" {
        val path = Arb.virtualAudioFile().next()
        val original = FXAudioItem(path, AudioItemTestFactory.nextTestId())

        val encoded = json.encodeToString(ObservableAudioItemSerializer, original)
        val decoded = json.decodeFromString(ObservableAudioItemSerializer, encoded)

        decoded.id shouldBe original.id
        decoded.path shouldBe original.path
        decoded.title shouldBe original.title
        decoded.duration shouldBe original.duration
        decoded.bitRate shouldBe original.bitRate
        decoded.artist shouldBe original.artist
        decoded.album shouldBe original.album
        decoded.genre shouldBe original.genre
        decoded.playCount shouldBe original.playCount
    }

    "ObservableAudioItemMapSerializer round-trip preserves the map entries" {
        val path1 = Arb.virtualAudioFile().next()
        val path2 = Arb.virtualAudioFile().next()
        val id1 = AudioItemTestFactory.nextTestId()
        val id2 = AudioItemTestFactory.nextTestId()
        val item1 = FXAudioItem(path1, id1)
        val item2 = FXAudioItem(path2, id2)
        val originalMap = mapOf(id1 to item1, id2 to item2)

        val encoded = json.encodeToString(ObservableAudioItemMapSerializer, originalMap)
        val decoded = json.decodeFromString(ObservableAudioItemMapSerializer, encoded)

        decoded.size shouldBe 2
        decoded[id1]!!.id shouldBe item1.id
        decoded[id1]!!.title shouldBe item1.title
        decoded[id2]!!.id shouldBe item2.id
        decoded[id2]!!.title shouldBe item2.title
    }
})