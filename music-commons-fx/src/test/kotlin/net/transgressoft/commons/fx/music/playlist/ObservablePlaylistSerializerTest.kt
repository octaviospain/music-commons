package net.transgressoft.commons.fx.music.playlist

import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * Tests for [ObservablePlaylistSerializer] verifying golden JSON structure and round-trip fidelity.
 */
internal class ObservablePlaylistSerializerTest : StringSpec({

    val json = Json.Default

    val goldenJson =
        """
        {
            "id": 1,
            "isDirectory": false,
            "name": "My Playlist",
            "audioItemIds": [10, 20],
            "playlistIds": []
        }
        """.trimIndent()

    "ObservablePlaylistSerializer deserializes golden JSON with correct field values" {
        val playlist = json.decodeFromString(ObservablePlaylistSerializer, goldenJson)

        playlist.id shouldBe 1
        playlist.isDirectory shouldBe false
        playlist.name shouldBe "My Playlist"
        // audioItems are deserialized as DummyAudioItem placeholders, resolved later by the hierarchy — verify count only
        playlist.audioItems.size shouldBe 2
        playlist.playlists.isEmpty() shouldBe true
    }

    "ObservablePlaylistSerializer encodes all required JSON fields" {
        val playlist = DummyPlaylist(id = 5, isDirectory = true, name = "Rock")

        val encoded = json.encodeToString(ObservablePlaylistSerializer, playlist)

        encoded shouldContainJsonKey "id"
        encoded shouldContainJsonKey "isDirectory"
        encoded shouldContainJsonKey "name"
        encoded shouldContainJsonKey "audioItemIds"
        encoded shouldContainJsonKey "playlistIds"
    }

    "ObservablePlaylistSerializer round-trip preserves all fields" {
        val original = DummyPlaylist(id = 3, isDirectory = false, name = "Favorites")

        val encoded = json.encodeToString(ObservablePlaylistSerializer, original)
        val decoded = json.decodeFromString(ObservablePlaylistSerializer, encoded)

        decoded.id shouldBe original.id
        decoded.isDirectory shouldBe original.isDirectory
        decoded.name shouldBe original.name
        decoded.audioItems.size shouldBe 0
        decoded.playlists.isEmpty() shouldBe true
    }

    "ObservablePlaylistMapSerializer round-trip preserves map entries" {
        val playlist1 = DummyPlaylist(id = 1, name = "Rock")
        val playlist2 = DummyPlaylist(id = 2, name = "Jazz")
        val originalMap = mapOf(1 to playlist1, 2 to playlist2)

        val encoded = json.encodeToString(ObservablePlaylistMapSerializer, originalMap)
        val decoded = json.decodeFromString(ObservablePlaylistMapSerializer, encoded)

        decoded.size shouldBe 2
        decoded[1]!!.id shouldBe 1
        decoded[1]!!.name shouldBe "Rock"
        decoded[2]!!.id shouldBe 2
        decoded[2]!!.name shouldBe "Jazz"
    }
})