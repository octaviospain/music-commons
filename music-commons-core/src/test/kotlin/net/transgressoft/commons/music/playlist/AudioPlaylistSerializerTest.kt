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
        // Decoded as ImmutablePlaylist stubs; audioItemIds carry the IDs for deferred resolution
        (playlist as ImmutablePlaylist).audioItemIds shouldBe listOf(10, 20)
        playlist.playlists.isEmpty() shouldBe true
    }

    "AudioPlaylistSerializer round-trip serialization produces equal entity fields" {
        val nestedPlaylist = ImmutablePlaylist(6, isDirectory = false, name = "Sub Playlist")
        val playlist =
            ImmutablePlaylist(
                id = 5,
                isDirectory = true,
                name = "My Directory",
                audioItemIds = listOf(100),
                playlistIds = setOf(6),
                playlists = setOf(nestedPlaylist)
            )

        val encoded = json.encodeToString(AudioPlaylistMapSerializer, mapOf(5 to playlist))
        val decoded = json.decodeFromString(AudioPlaylistMapSerializer, encoded)

        decoded.size shouldBe 1
        decoded.containsKey(5) shouldBe true

        val decodedPlaylist = decoded.getValue(5) as ImmutablePlaylist

        decodedPlaylist.id shouldBe 5
        decodedPlaylist.isDirectory shouldBe true
        decodedPlaylist.name shouldBe "My Directory"
        decodedPlaylist.audioItemIds shouldBe listOf(100)
        decodedPlaylist.playlistIds shouldBe setOf(6)
    }

    "AudioPlaylistSerializer encodes audio item ids not full objects" {
        val playlist =
            ImmutablePlaylist(
                id = 3,
                isDirectory = false,
                name = "Encoded Playlist",
                audioItemIds = listOf(10, 20)
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