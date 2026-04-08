package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.json.lirpSerializer
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.testfx.api.FxToolkit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json

/**
 * Tests for [FXPlaylist] serializer verifying JSON structure and round-trip fidelity using [lirpSerializer].
 */
@ExperimentalCoroutinesApi
internal class ObservablePlaylistSerializerTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    val json = Json.Default

    @Suppress("UNCHECKED_CAST")
    val serializer = lirpSerializer(FXPlaylist(0, "", false)) as kotlinx.serialization.KSerializer<ObservablePlaylist>

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "FXPlaylist lirpSerializer encodes all required JSON fields" {
        val hierarchy = FXPlaylistHierarchy()
        val playlist = hierarchy.createPlaylist("Rock")

        val encoded = json.encodeToString(serializer, playlist)

        encoded shouldContainJsonKey "id"
        encoded shouldContainJsonKey "isDirectory"
        encoded shouldContainJsonKey "name"
        encoded shouldContainJsonKey "audioItems"
        encoded shouldContainJsonKey "playlists"

        hierarchy.close()
    }

    "FXPlaylist lirpSerializer round-trip preserves id, name, and directory flag" {
        val hierarchy = FXPlaylistHierarchy()
        val playlist = hierarchy.createPlaylist("Favorites")

        val encoded = json.encodeToString(serializer, playlist)
        val decoded = json.decodeFromString(serializer, encoded)

        decoded.id shouldBe playlist.id
        decoded.isDirectory shouldBe playlist.isDirectory
        decoded.name shouldBe playlist.name
        decoded.audioItems.size shouldBe 0
        decoded.playlists.isEmpty() shouldBe true

        hierarchy.close()
    }

    "ObservablePlaylistMapSerializer round-trip preserves map entries" {
        val hierarchy = FXPlaylistHierarchy()
        val playlist1 = hierarchy.createPlaylist("Rock")
        val playlist2 = hierarchy.createPlaylist("Jazz")
        val originalMap = mapOf(playlist1.id to playlist1, playlist2.id to playlist2)

        val encoded = json.encodeToString(ObservablePlaylistMapSerializer, originalMap)
        val decoded = json.decodeFromString(ObservablePlaylistMapSerializer, encoded)

        decoded.size shouldBe 2
        decoded.values.any { it.name == "Rock" } shouldBe true
        decoded.values.any { it.name == "Jazz" } shouldBe true

        hierarchy.close()
    }
})