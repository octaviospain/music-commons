package net.transgressoft.commons.music.playlist

import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tests for [MutableAudioPlaylistSerializer] covering golden JSON fixture deserialization
 * and round-trip serialization fidelity.
 */
@DisplayName("AudioPlaylistSerializer")
internal class AudioPlaylistSerializerTest : StringSpec({

    val json = Json { prettyPrint = false }

    "AudioPlaylistSerializer decodes golden JSON fixture with expected field values" {
        val goldenJson =
            """
            {
              "1": {
                "id": 1,
                "isDirectory": false,
                "name": "Test Playlist",
                "audioItemIds": [10, 20],
                "playlistIds": []
              }
            }
            """.trimIndent()

        val result = json.decodeFromString(AudioPlaylistMapSerializer, goldenJson)

        result.size shouldBe 1
        result.containsKey(1) shouldBe true

        val playlist = result.getValue(1)

        playlist.id shouldBe 1
        playlist.isDirectory shouldBe false
        playlist.name shouldBe "Test Playlist"
        playlist.audioItems.size shouldBe 2
        playlist.audioItems.map { it.id }.containsAll(listOf(10, 20)) shouldBe true
        playlist.playlists.isEmpty() shouldBe true
    }

    "AudioPlaylistSerializer round-trip serialization produces equal entity fields" {
        val nestedPlaylist = DummyPlaylist(6, isDirectory = false, name = "Sub Playlist")
        val audioItem1 = DummyAudioItem(100)
        val playlist =
            DummyPlaylist(
                id = 5,
                isDirectory = true,
                name = "My Directory",
                audioItems = listOf(audioItem1),
                playlists = setOf(nestedPlaylist)
            )

        val encoded = json.encodeToString(AudioPlaylistMapSerializer, mapOf(5 to playlist))
        val decoded = json.decodeFromString(AudioPlaylistMapSerializer, encoded)

        decoded.size shouldBe 1
        decoded.containsKey(5) shouldBe true

        val decodedPlaylist = decoded.getValue(5)

        decodedPlaylist.id shouldBe 5
        decodedPlaylist.isDirectory shouldBe true
        decodedPlaylist.name shouldBe "My Directory"
        decodedPlaylist.audioItems.size shouldBe 1
        decodedPlaylist.audioItems.first().id shouldBe 100
        decodedPlaylist.playlists.size shouldBe 1
        decodedPlaylist.playlists.first().id shouldBe 6
    }

    "AudioPlaylistSerializer encodes audio item ids not full objects" {
        val audioItem1 = DummyAudioItem(10)
        val audioItem2 = DummyAudioItem(20)
        val playlist =
            DummyPlaylist(
                id = 3,
                isDirectory = false,
                name = "Encoded Playlist",
                audioItems = listOf(audioItem1, audioItem2)
            )

        val encoded = json.encodeToString(AudioPlaylistMapSerializer, mapOf(3 to playlist))

        val jsonElement = Json.parseToJsonElement(encoded)
        val playlistJson = jsonElement.jsonObject["3"]!!.jsonObject
        playlistJson.containsKey("audioItemIds") shouldBe true
        playlistJson.containsKey("audioItems") shouldBe false
        val audioItemIds = playlistJson["audioItemIds"]!!.jsonArray.map { it.jsonPrimitive.int }
        audioItemIds shouldBe listOf(10, 20)
    }
})