package net.transgressoft.commons.fx.music

import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.music.audio.WindowsPathException
import net.transgressoft.commons.music.common.OsDetector
import net.transgressoft.lirp.event.ReactiveScope
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Tests for [FXMusicLibrary] verifying builder construction, JavaFX property exposure, and lifecycle.
 */
@ExperimentalCoroutinesApi
internal class FXMusicLibraryTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
        FxToolkit.cleanupStages()
    }

    suspend fun eventuallyAfterFxEvents(assertion: () -> Unit) {
        eventually(500.milliseconds) {
            WaitForAsyncUtils.waitForFxEvents()
            assertion()
        }
    }

    "FXMusicLibrary builder creates volatile library by default" {
        val library = FXMusicLibrary.builder().build()

        library.audioLibrary().shouldNotBeNull()
        library.playlistHierarchy().shouldNotBeNull()
        library.waveformRepository().shouldNotBeNull()
        library.audioItemsProperty.shouldNotBeNull()
        library.playlistsProperty.shouldNotBeNull()

        library.close()
    }

    "FXMusicLibrary exposes JavaFX properties" {
        val library = FXMusicLibrary.builder().build()

        library.audioItemsProperty.shouldNotBeNull()
        library.emptyLibraryProperty.shouldNotBeNull()
        library.artistsProperty.shouldNotBeNull()
        library.artistCatalogsProperty.shouldNotBeNull()
        library.albumsProperty.shouldNotBeNull()
        library.albumCountProperty.shouldNotBeNull()
        library.playlistsProperty.shouldNotBeNull()

        library.emptyLibraryProperty.get() shouldBe true
        library.audioItemsProperty.isEmpty() shouldBe true
        library.playlistsProperty.isEmpty() shouldBe true

        library.close()
    }

    "FXMusicLibrary curated methods work for audio items and playlists" {
        val library = FXMusicLibrary.builder().build()

        val audioPath = Arb.virtualAudioFile().next()
        val audioItem = library.audioItemFromFile(audioPath)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        audioItem.shouldNotBeNull()
        audioItem.id shouldNotBe 0
        library.audioLibrary().size() shouldBe 1

        eventuallyAfterFxEvents {
            library.audioItemsProperty.isEmpty() shouldBe false
        }

        val playlist = library.createPlaylist("My Playlist")
        playlist.addAudioItem(audioItem)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        library.findPlaylistByName("My Playlist") shouldBePresent {
            it.name shouldBe "My Playlist"
        }

        library.close()
    }

    "FXMusicLibrary resolves playlist self-references after JSON deserialization" {
        val playlistFile = tempfile("playlist-self-ref-test", ".json").also { it.deleteOnExit() }
        playlistFile.writeText(
            """
            {
                "1": { "id": 1, "name": "ROOT", "isDirectory": true, "audioItems": [], "playlists": [2] },
                "2": { "id": 2, "name": "CHILD", "isDirectory": false, "audioItems": [], "playlists": [] }
            }
            """.trimIndent()
        )

        val library =
            FXMusicLibrary.builder()
                .playlistHierarchyJsonFile(playlistFile)
                .build()

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        val root = library.findPlaylistByName("ROOT")
        root shouldBePresent {
            it.playlists.size shouldBe 1
            it.playlists.map { p -> p.name } shouldContain "CHILD"
        }

        library.close()
    }

    "FXMusicLibrary close releases all resources" {
        val library = FXMusicLibrary.builder().build()

        val audioItem = library.audioItemFromFile(Arb.virtualAudioFile().next())

        testDispatcher.scheduler.advanceUntilIdle()

        library.audioLibrary().size() shouldBe 1
        WaitForAsyncUtils.waitForFxEvents()
        val sizeBeforeClose = library.audioItemsProperty.size

        library.close()

        // After close, the library retains already-added items but does not track new ones
        library.audioLibrary().size() shouldBe 1
        library.audioLibrary().add(library.audioItemFromFile(Arb.virtualAudioFile().next()))
        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        library.audioItemsProperty.size shouldBe sizeBeforeClose
    }

    "FXMusicLibrary.audioItemFromFile throws WindowsPathException before delegating when isWindows=true" {
        OsDetector.withOverriddenIsWindows(true) {
            val library = FXMusicLibrary.builder().build()
            val fs = Jimfs.newFileSystem(Configuration.unix())
            library.use {
                fs.use {
                    val forbidden = fs.getPath("/tmp/bad|name.mp3")
                    shouldThrow<WindowsPathException> { library.audioItemFromFile(forbidden) }
                }
            }
        }
    }
})