package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist.Companion.of
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.music.audio.shouldEqual
import net.transgressoft.commons.persistence.json.JsonFileRepository
import net.transgressoft.commons.persistence.json.JsonRepository
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import javafx.application.Platform
import org.testfx.api.FxToolkit
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class ObservableAudioLibraryTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var jsonFileRepository: JsonRepository<Int, ObservableAudioItem>
    lateinit var repository: ObservableAudioLibrary

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
        FxToolkit.registerPrimaryStage()
    }

    beforeEach {
        jsonFile = tempfile("observableAudioLibrary-test", ".json").also { it.deleteOnExit() }
        jsonFileRepository = JsonFileRepository(jsonFile, ObservableAudioItemMapSerializer)
        repository = ObservableAudioLibrary(jsonFileRepository)
    }

    afterEach {
        jsonFileRepository.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "Creates an observable audio item and serialize itself" {
        val fxAudioItem = repository.createFromFile(Arb.virtualAudioFile().next())

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile shouldEqual fxAudioItem.asJsonKeyValue()

        fxAudioItem.title = "New title"

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText() should {
            it.shouldEqualJson(fxAudioItem.asJsonKeyValue())
            it.shouldContainOnlyOnce("title\": \"New title")
        }
    }

    "Expose changes on its audioItemsProperty" {
        val audioItemsProperty = repository.audioItemsProperty
        repository.emptyLibraryProperty.get() shouldBe true
        repository.audioItemsProperty.isEmpty() shouldBe true

        val fxAudioItem = repository.createFromFile(Arb.virtualAudioFile().next())

        testDispatcher.scheduler.advanceUntilIdle()

        audioItemsProperty.contains(fxAudioItem) shouldBe true
        repository.emptyLibraryProperty.get() shouldBe false

        fxAudioItem.title = "New title"

        testDispatcher.scheduler.advanceUntilIdle()
        var foundItem = audioItemsProperty.get().find { it.id == fxAudioItem.id }
        foundItem.shouldNotBeNull()
        foundItem.title shouldBe "New title"

        repository.remove(fxAudioItem)
        audioItemsProperty.isEmpty() shouldBe true
        repository.emptyLibraryProperty.get() shouldBe true
    }

    "Contains expected items after loading from JsonFileRepository" {
        val createdItems = List(10) { repository.createFromFile(Arb.virtualAudioFile().next()) }

        testDispatcher.scheduler.advanceUntilIdle()

        val expectedIds = createdItems.map { it.id }.toSet()
        val expectedArtists = createdItems.flatMap { it.artistsInvolved }.toSet()

        // Recreate the library from the same JsonRepository
        repository = ObservableAudioLibrary(jsonFileRepository)

        testDispatcher.scheduler.advanceUntilIdle()

        // Assert items are all present after reloading
        repository.size() shouldBe createdItems.size
        val loadedIds = repository.audioItemsProperty.map { observableAudioItem: ObservableAudioItem -> observableAudioItem.id }.toSet()
        loadedIds shouldContainOnly expectedIds

        expectedIds.forEach { id ->
            repository.findById(id) shouldBePresent { found -> found.id shouldBe id }
        }

        eventually(1.seconds) {
            repository.artistsProperty.size shouldBe expectedArtists.size
            repository.artistsProperty shouldContainOnly expectedArtists
        }
    }

    "Rapid concurrent additions to library maintain consistency" {
        val audioFiles = List(200) { Arb.virtualAudioFile().next() }

        // Rapid additions that could trigger concurrent modifications
        val addedItems =
            audioFiles.map { file ->
                repository.createFromFile(file)
            }

        testDispatcher.scheduler.advanceUntilIdle()

        eventually(1.seconds) {
            Platform.runLater {
                repository.audioItemsProperty.size shouldBe 20
                repository.audioItemsProperty.map { it: ObservableAudioItem -> it.id } shouldContainOnly addedItems.map { it.id }
            }
        }

        // Rapid removals
        addedItems.take(100).forEach { repository.remove(it) }

        testDispatcher.scheduler.advanceUntilIdle()

        eventually(1.seconds) {
            Platform.runLater {
                repository.audioItemsProperty.size shouldBe 100
            }
        }
    }

    /**
     * Creates audio items for multiple artists and returns them grouped by artist.
     *
     * @param artistConfigs Map of artist name to number of items to create for that artist
     * @return Map of Artist to List of created ObservableAudioItems
     */
    fun createItemsByArtist(artistConfigs: Map<String, Int>): Map<net.transgressoft.commons.music.audio.Artist, List<ObservableAudioItem>> =
        artistConfigs.flatMap { (artistName, itemCount) ->
            val artist = of(artistName)
            val album = ImmutableAlbum("$artistName Album", artist)
            List(itemCount) {
                repository.createFromFile(
                    Arb.virtualAudioFile {
                        this.artist = artist
                        this.album = album
                    }.next()
                )
            }.map { artist to it }
        }.groupBy({ it.first }, { it.second })

    "Artists are removed from artistsProperty only when all items with that artist are removed" {
        val itemsByArtist =
            createItemsByArtist(
                mapOf(
                    "Shared Artist A" to 3, // 3 with "Shared Artist A",
                    "Unique Artist B" to 1, // 1 with "Unique Artist B",
                    "Shared Artist C" to 2, // 2 with "Shared Artist C"
                    "Unique Artist D" to 1 //  1 with "Unique Artist D"
                )
            )

        val artistA = itemsByArtist.keys.first { it.name == "Shared Artist A" }
        val artistB = itemsByArtist.keys.first { it.name == "Unique Artist B" }
        val artistC = itemsByArtist.keys.first { it.name == "Shared Artist C" }
        val artistD = itemsByArtist.keys.first { it.name == "Unique Artist D" }

        val itemsA = itemsByArtist[artistA]!!
        val itemsB = itemsByArtist[artistB]!!
        val itemsC = itemsByArtist[artistC]!!
        val itemsD = itemsByArtist[artistD]!!

        testDispatcher.scheduler.advanceUntilIdle()

        eventually(1.seconds) {
            repository.artistsProperty shouldContainOnly setOf(artistA, artistB, artistC, artistD)
        }

        // Remove one item with "Shared Artist A" - artist should still be in artistsProperty
        repository.remove(itemsA[0])
        testDispatcher.scheduler.advanceUntilIdle()

        eventually(1.seconds) {
            repository.artistsProperty shouldContainOnly
                setOf(
                    artistA, // Still present because itemsA[1] and itemsA[2] have this artist
                    artistB,
                    artistC,
                    artistD
                )
        }

        // Remove another item with "Shared Artist A" - artist should still be in artistsProperty
        repository.remove(itemsA[1])
        testDispatcher.scheduler.advanceUntilIdle()

        eventually(1.seconds) {
            repository.artistsProperty shouldContainOnly
                setOf(
                    artistA, // Still present because itemsA[2] still has this artist
                    artistB,
                    artistC,
                    artistD
                )
        }

        // Remove the unique artist item - artist should be removed from artistsProperty
        repository.remove(itemsB[0])
        testDispatcher.scheduler.advanceUntilIdle()

        eventually(1.seconds) {
            repository.artistsProperty shouldContainOnly
                setOf(
                    artistA, // Still present. B was removed
                    artistC,
                    artistD
                )
        }

        // Remove the last item with "Shared Artist A" - now artist should be removed
        repository.remove(itemsA[2])
        testDispatcher.scheduler.advanceUntilIdle()

        eventually(1.seconds) {
            repository.artistsProperty shouldContainOnly
                setOf(
                    artistC, // Shared Artist C
                    artistD // Unique Artist D
                )
        }

        // Remove one of the "Shared Artist C" items - artist should remain
        repository.remove(itemsC[0])
        testDispatcher.scheduler.advanceUntilIdle()

        eventually(1.seconds) {
            repository.artistsProperty shouldContainOnly
                setOf(
                    artistC, // Shared Artist C still present via itemsC[1]
                    artistD
                )
        }

        // Remove remaining items
        repository.remove(itemsC[1])
        repository.remove(itemsD[0])
        testDispatcher.scheduler.advanceUntilIdle()

        eventually(1.seconds) {
            repository.artistsProperty.isEmpty() shouldBe true
        }
    }
})