package net.transgressoft.commons.music.playlist

import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tests for the lirp-based [MutablePlaylist] serializer covering golden JSON fixture deserialization
 * and round-trip serialization fidelity using [lirpSerializer].
 */
@DisplayName("AudioPlaylistSerializer")
internal class AudioPlaylistSerializerTest : StringSpec({

    val json = Json { prettyPrint = false }

    "AudioPlaylistSerializer decodes golden JSON fixture as MutablePlaylist" {
        val goldenJson =
            """
            {
              "1": {
                "id": 1,
                "isDirectory": false,
                "name": "Test Playlist",
                "audioItems": [],
                "playlists": []
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
        playlist.audioItems.isEmpty() shouldBe true
        playlist.playlists.isEmpty() shouldBe true
    }

    "AudioPlaylistSerializer round-trip serialization produces equal entity fields" {
        val playlistHierarchy = DefaultPlaylistHierarchy()

        val nestedPlaylist = playlistHierarchy.createPlaylist("Sub Playlist")
        val encoded = json.encodeToString(AudioPlaylistMapSerializer, mapOf(nestedPlaylist.id to nestedPlaylist))

        val decoded = json.decodeFromString(AudioPlaylistMapSerializer, encoded)

        decoded.size shouldBe 1
        decoded.containsKey(nestedPlaylist.id) shouldBe true

        val decodedPlaylist = decoded.getValue(nestedPlaylist.id)

        decodedPlaylist.id shouldBe nestedPlaylist.id
        decodedPlaylist.isDirectory shouldBe false
        decodedPlaylist.name shouldBe "Sub Playlist"
        decodedPlaylist.audioItems.isEmpty() shouldBe true
        decodedPlaylist.playlists.isEmpty() shouldBe true

        playlistHierarchy.close()
    }

    "AudioPlaylistSerializer encodes audio item ids not full objects" {
        val playlistHierarchy = DefaultPlaylistHierarchy()
        val playlist = playlistHierarchy.createPlaylist("Encoded Playlist")

        val encoded = json.encodeToString(AudioPlaylistMapSerializer, mapOf(playlist.id to playlist))

        val jsonElement = Json.parseToJsonElement(encoded)
        val playlistJson = jsonElement.jsonObject["${playlist.id}"]!!.jsonObject
        playlistJson.containsKey("audioItems") shouldBe true
        playlistJson.containsKey("audioItemIds") shouldBe false
        val audioItemIds = playlistJson["audioItems"]!!.jsonArray.map { it.jsonPrimitive.content }
        audioItemIds shouldBe emptyList()

        playlistHierarchy.close()
    }
})