package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.AudioItemTestFactory
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.util.toJsonUri
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.arbitrary.next
import java.time.temporal.ChronoUnit.SECONDS
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * Tests for [ObservableAudioItemSerializer] verifying golden JSON structure and round-trip fidelity.
 */
internal class ObservableAudioItemSerializerTest : StringSpec({
    val files = virtualFiles()

    // Bind the serializer to the Jimfs filesystem so encoded `file://` URIs deserialize back to
    // the same Jimfs path tree on round-trip.
    val fxSerializer = ObservableAudioItemSerializer(files.fileSystem)
    val json =
        Json {
            serializersModule =
                SerializersModule {
                    polymorphic(ObservableAudioItem::class, fxSerializer)
                }
        }
    val fxMapSerializer = MapSerializer(Int.serializer(), fxSerializer)

    "ObservableAudioItemSerializer encodes all required JSON fields" {
        val path = files.virtualAudioFile().next()
        val fxAudioItem = FXAudioItemTestBridge.createFxAudioItem(path, AudioItemTestFactory.nextTestId(), files.metadataIO)

        val encoded = json.encodeToString(fxSerializer, fxAudioItem)

        encoded shouldContainJsonKey "path"
        encoded shouldContainJsonKey "id"
        encoded shouldContainJsonKey "title"
        encoded shouldContainJsonKey "duration"
        encoded shouldContainJsonKey "bitRate"
        encoded shouldContainJsonKey "artist"
        encoded shouldContainJsonKey "album"
        encoded shouldContainJsonKey "genres"
        encoded shouldContainJsonKey "dateOfCreation"
        encoded shouldContainJsonKey "lastDateModified"
        encoded shouldContainJsonKey "playCount"
    }

    "ObservableAudioItemSerializer round-trip preserves all fields" {
        val path = files.virtualAudioFile().next()
        val original = FXAudioItemTestBridge.createFxAudioItem(path, AudioItemTestFactory.nextTestId(), files.metadataIO)

        val encoded = json.encodeToString(fxSerializer, original)
        val decoded = json.decodeFromString(fxSerializer, encoded)

        decoded.id shouldBe original.id
        decoded.path shouldBe original.path
        decoded.title shouldBe original.title
        decoded.duration shouldBe original.duration
        decoded.bitRate shouldBe original.bitRate
        decoded.artist shouldBe original.artist
        decoded.album shouldBe original.album
        decoded.genres shouldBe original.genres
        decoded.comments shouldBe original.comments
        decoded.trackNumber shouldBe original.trackNumber
        decoded.discNumber shouldBe original.discNumber
        decoded.bpm shouldBe original.bpm
        decoded.encoder shouldBe original.encoder
        decoded.encoding shouldBe original.encoding
        decoded.dateOfCreation.truncatedTo(SECONDS) shouldBe original.dateOfCreation.truncatedTo(SECONDS)
        decoded.lastDateModified.truncatedTo(SECONDS) shouldBe original.lastDateModified.truncatedTo(SECONDS)
        decoded.playCount shouldBe original.playCount
    }

    "ObservableAudioItemSerializer deserializes legacy single-genre field into genres set" {
        val path =
            files.virtualAudioFile {
                this.genres = setOf(Genre.Rock)
            }.next()
        val original = FXAudioItemTestBridge.createFxAudioItem(path, AudioItemTestFactory.nextTestId(), files.metadataIO)

        val encoded = json.encodeToString(fxSerializer, original)

        // Replace "genres" array with legacy "genre" single-string field
        val jsonTree = Json.parseToJsonElement(encoded).jsonObject
        val legacyItem =
            buildJsonObject {
                for ((k, v) in jsonTree) {
                    if (k != "genres") put(k, v)
                }
                put("genre", "Rock")
            }

        val decoded = json.decodeFromString(fxSerializer, legacyItem.toString())
        decoded.genres shouldBe setOf(Genre.Rock)
    }

    "ObservableAudioItemSerializer inherits file:// URI format from ReactiveAudioItem.toJsonObject" {
        val path = files.virtualAudioFile().next()
        val fxAudioItem = FXAudioItemTestBridge.createFxAudioItem(path, AudioItemTestFactory.nextTestId(), files.metadataIO)

        val encoded = json.encodeToString(fxSerializer, fxAudioItem)

        encoded shouldContain "\"path\":\"file://"
    }

    "ObservableAudioItemSerializer deserialization succeeds for a path that no longer exists on disk" {
        // Phase 40-02/03 semantics: existence checks live in FXAudioLibrary.createFromFile; pure-data
        // deserialization succeeds even when the path is offline. Deserialized items arrive with
        // library = null and coverImageBytes = null until the owning library wires the back-ref.
        //
        // The path lives on the Jimfs filesystem the serializer is bound to — the URI must be parseable
        // by Jimfs's URI handler, so use a Jimfs-native synthetic path rather than a real default-FS
        // temp file. Mixing filesystems produced `/work/C:/...` style paths on Windows runners that
        // tripped the Windows path validator with the embedded `:` from the drive letter.
        val gonePath = files.fileSystem.getPath("/music/offline-ghost.mp3")
        // toJsonUri synthesizes a file:// URI even for non-default filesystems, matching what
        // production serialization emits and what toPathFromJsonUri's `file://` guard accepts.
        val pathUri = gonePath.toJsonUri()
        val offlineDriveJson =
            """
            {
              "id": 1,
              "path": "$pathUri",
              "title": "Ghost",
              "duration": 180,
              "bitRate": 320,
              "artist": {"name": "Artist", "countryCode": "US"},
              "album": {
                "name": "Album",
                "albumArtist": {"name": "Artist", "countryCode": "US"},
                "isCompilation": false,
                "year": null,
                "label": {"name": "Label"}
              },
              "genres": [],
              "comments": null,
              "trackNumber": null,
              "discNumber": null,
              "bpm": null,
              "encoder": null,
              "encoding": null,
              "dateOfCreation": 1000000,
              "lastDateModified": 1000000,
              "playCount": 0
            }
            """.trimIndent()

        val decoded = json.decodeFromString(fxSerializer, offlineDriveJson) as FXAudioItem
        decoded.id shouldBe 1
        decoded.title shouldBe "Ghost"
        decoded.coverImageBytes shouldBe null
    }

    "ObservableAudioItemMapSerializer round-trip preserves the map entries" {
        val path1 = files.virtualAudioFile().next()
        val path2 = files.virtualAudioFile().next()
        val id1 = AudioItemTestFactory.nextTestId()
        val id2 = AudioItemTestFactory.nextTestId()
        val item1 = FXAudioItemTestBridge.createFxAudioItem(path1, id1, files.metadataIO)
        val item2 = FXAudioItemTestBridge.createFxAudioItem(path2, id2, files.metadataIO)
        val originalMap = mapOf(id1 to item1, id2 to item2)

        val encoded = json.encodeToString(fxMapSerializer, originalMap)
        val decoded = json.decodeFromString(fxMapSerializer, encoded)

        decoded.size shouldBe 2
        decoded[id1]!!.id shouldBe item1.id
        decoded[id1]!!.title shouldBe item1.title
        decoded[id2]!!.id shouldBe item2.id
        decoded[id2]!!.title shouldBe item2.title
    }
})