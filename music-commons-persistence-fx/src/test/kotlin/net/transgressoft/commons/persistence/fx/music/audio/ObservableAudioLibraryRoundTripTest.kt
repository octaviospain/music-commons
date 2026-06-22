package net.transgressoft.commons.persistence.fx.music.audio

import net.transgressoft.commons.fx.music.FXMusicLibrary
import net.transgressoft.commons.fx.music.audio.ObservableArtistCatalog
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioLibrary
import net.transgressoft.commons.fx.music.createItemsByArtist
import net.transgressoft.commons.fx.music.createItemsWithMultipleAlbums
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist.Companion.of
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.util.InvalidAudioFilePathException
import net.transgressoft.lirp.entity.toIds
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.arbitrary.next
import javafx.collections.ListChangeListener
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class ObservableAudioLibraryRoundTripTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()
    lateinit var jsonFile: File
    lateinit var library: FXMusicLibrary
    lateinit var repository: ObservableAudioLibrary

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    beforeEach {
        jsonFile = tempfile("observableAudioLibrary-test", ".json").also { it.deleteOnExit() }
        library =
            FXMusicLibrary.builder()
                .audioRepository(JsonFileRepository(jsonFile, ObservableAudioItemMapSerializer))
                .metadataIO(files.metadataIO)
                .build()
        repository = library.audioLibrary()
    }

    afterEach {
        library.close()
    }

    "Expose changes on its audioItemsProperty" {
        val audioItemsProperty = repository.audioItemsProperty
        repository.emptyLibraryProperty.get() shouldBe true
        repository.audioItemsProperty.isEmpty() shouldBe true

        val fxAudioItem = repository.createFromFile(files.virtualAudioFile().next())

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        audioItemsProperty.contains(fxAudioItem) shouldBe true
        repository.emptyLibraryProperty.get() shouldBe false

        fxAudioItem.title = "New title"

        reactive.advance()
        var foundItem = audioItemsProperty.get().find { it.id == fxAudioItem.id }
        foundItem.shouldNotBeNull()
        foundItem.title shouldBe "New title"

        repository.remove(fxAudioItem)
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        audioItemsProperty.isEmpty() shouldBe true
        repository.emptyLibraryProperty.get() shouldBe true
    }

    "Contains expected items after loading from JsonFileRepository" {
        val createdItems = List(10) { repository.createFromFile(files.virtualAudioFile().next()) }

        reactive.advance()

        val expectedIds = createdItems.toIds()
        val expectedArtists = createdItems.flatMap { it.artistsInvolved }.toSet()

        // Close the existing library before recreating from a fresh repository over the same file
        library.close()
        library =
            FXMusicLibrary.builder()
                .audioRepository(JsonFileRepository(jsonFile, ObservableAudioItemMapSerializer))
                .metadataIO(files.metadataIO)
                .build()
        repository = library.audioLibrary()

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

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
        val audioFiles = List(50) { files.virtualAudioFile().next() }

        // Rapid additions that could trigger concurrent modifications
        val addedItems =
            audioFiles.map { file ->
                repository.createFromFile(file)
            }

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.audioItemsProperty.size shouldBe 50
            repository.audioItemsProperty.map { it: ObservableAudioItem -> it.id } shouldContainOnly addedItems.toIds()
        }

        // Rapid removals
        addedItems.take(25).forEach { repository.remove(it) }

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.audioItemsProperty.size shouldBe 25
        }
    }

    "ObservableAudioLibrary coalesces create bursts while preserving derived observable state" {
        val audioItemsChangeCount = AtomicInteger()
        repository.audioItemsProperty.addListener(
            ListChangeListener<ObservableAudioItem> {
                audioItemsChangeCount.incrementAndGet()
            }
        )

        val itemsByArtist =
            repository.createItemsByArtist(
                files,
                mapOf("Burst Alpha" to 25, "Burst Beta" to 25, "Burst Gamma" to 25)
            )
        val expectedItems = itemsByArtist.values.flatten()
        val expectedArtists = itemsByArtist.keys
        val expectedAlbums = expectedItems.map { it.album }.toSet()

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.audioItemsProperty.size shouldBe expectedItems.size
            repository.audioItemsProperty.map { audioItem: ObservableAudioItem -> audioItem.id } shouldContainOnly expectedItems.toIds()
            repository.artistsProperty shouldContainOnly expectedArtists
            repository.artistCatalogsProperty.map { catalog: ObservableArtistCatalog -> catalog.artist }.toSet() shouldContainOnly expectedArtists
            repository.albumsProperty shouldContainOnly expectedAlbums
            repository.albumCountProperty.get() shouldBe expectedAlbums.size
        }

        audioItemsChangeCount.get() shouldBe 1
    }

    "ObservableAudioLibrary contains catalogs for all artists after adding items" {
        val itemsByArtist =
            repository.createItemsByArtist(
                files,
                mapOf("Alpha" to 2, "Beta" to 3, "Gamma" to 1)
            )

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.artistCatalogsProperty.size shouldBe 3

            val catalogArtists = repository.artistCatalogsProperty.get().map { it.artist }.toSet()
            catalogArtists shouldContainOnly itemsByArtist.keys

            itemsByArtist.forEach { (artist, items) ->
                val catalog = repository.artistCatalogsProperty.get().first { it.artist == artist }
                catalog.sizeProperty.get() shouldBe items.size
            }
        }
    }

    "ObservableAudioLibrary catalogs reflect correct album data with multiple albums per artist" {
        val artistName = "Multi Album Artist"
        val artist = of(artistName)
        val album1 = Album("First Album", artist)
        val album2 = Album("Second Album", artist)

        repository.createItemsWithMultipleAlbums(
            files,
            artistName,
            mapOf("First Album" to 3, "Second Album" to 2)
        )

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.artistCatalogsProperty.size shouldBe 1

            val catalog = repository.artistCatalogsProperty.get().first()
            catalog.artist shouldBe artist
            catalog.sizeProperty.get() shouldBe 5
            catalog.albumCountProperty.get() shouldBe 2
            catalog.albumsProperty shouldContainOnly setOf(album1, album2)

            catalog.albumAudioItemsProperty("First Album").size shouldBe 3
            catalog.albumAudioItemsProperty("Second Album").size shouldBe 2
        }
    }

    "ObservableAudioLibrary removes catalog when all items for artist are deleted" {
        val itemsByArtist =
            repository.createItemsByArtist(
                files,
                mapOf("Keep Artist" to 2, "Remove Artist" to 3)
            )

        val keepArtist = itemsByArtist.keys.first { it.name == "Keep Artist" }
        val removeArtist = itemsByArtist.keys.first { it.name == "Remove Artist" }
        val removeItems = itemsByArtist[removeArtist]!!

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.artistCatalogsProperty.size shouldBe 2
        }

        removeItems.forEach { repository.remove(it) }
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.artistCatalogsProperty.size shouldBe 1
            repository.artistCatalogsProperty.get().first().artist shouldBe keepArtist
        }
    }

    "ObservableAudioLibrary catalog sizeProperty decreases when item removed but artist remains" {
        val artistName = "Partial Remove"
        val items = repository.createItemsByArtist(files, mapOf(artistName to 3))[of(artistName)]!!

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            val catalog = repository.artistCatalogsProperty.get().first()
            catalog.sizeProperty.get() shouldBe 3
        }

        repository.remove(items[0])
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.artistCatalogsProperty.size shouldBe 1
            repository.artistCatalogsProperty.get().first().sizeProperty.get() shouldBe 2
        }
    }

    "ObservableAudioLibrary albumsProperty and albumCountProperty reflect aggregate across all catalogs" {
        val artist1 = of("Artist One")
        val artist2 = of("Artist Two")
        val album1A = Album("Album 1A", artist1)
        val album1B = Album("Album 1B", artist1)
        val album2A = Album("Album 2A", artist2)

        repository.createItemsWithMultipleAlbums(files, "Artist One", mapOf("Album 1A" to 2, "Album 1B" to 1))
        repository.createItemsWithMultipleAlbums(files, "Artist Two", mapOf("Album 2A" to 2))

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.albumCountProperty.get() shouldBe 3
            repository.albumsProperty shouldContainOnly setOf(album1A, album1B, album2A)
        }
    }

    "ObservableAudioLibrary artistCatalogsProperty reflects correct state after rapid multi-artist operations" {
        val itemsByArtist =
            repository.createItemsByArtist(
                files,
                mapOf("Rapid A" to 3, "Rapid B" to 2, "Rapid C" to 4, "Rapid D" to 1)
            )

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.artistCatalogsProperty.size shouldBe 4
        }

        val rapidB = itemsByArtist.keys.first { it.name == "Rapid B" }
        val rapidD = itemsByArtist.keys.first { it.name == "Rapid D" }

        itemsByArtist[rapidB]!!.forEach { repository.remove(it) }
        itemsByArtist[rapidD]!!.forEach { repository.remove(it) }

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        eventually(1.seconds) {
            repository.artistCatalogsProperty.size shouldBe 2
            val remainingArtists = repository.artistCatalogsProperty.get().map { it.artist.name }.toSet()
            remainingArtists shouldContainOnly setOf("Rapid A", "Rapid C")

            repository.artistCatalogsProperty.get().first { it.artist.name == "Rapid A" }.sizeProperty.get() shouldBe 3
            repository.artistCatalogsProperty.get().first { it.artist.name == "Rapid C" }.sizeProperty.get() shouldBe 4

            repository.albumCountProperty.get() shouldBeGreaterThanOrEqual 2
            repository.albumsProperty.size shouldBeGreaterThanOrEqual 1
        }
    }

    "Artists are removed from artistsProperty only when all items with that artist are removed" {
        val itemsByArtist =
            repository.createItemsByArtist(
                files,
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

        reactive.advance()

        eventually(1.seconds) {
            repository.artistsProperty shouldContainOnly setOf(artistA, artistB, artistC, artistD)
        }

        // Remove one item with "Shared Artist A" - artist should still be in artistsProperty
        repository.remove(itemsA[0])
        reactive.advance()

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
        reactive.advance()

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
        reactive.advance()

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
        reactive.advance()

        eventually(1.seconds) {
            repository.artistsProperty shouldContainOnly
                setOf(
                    artistC, // Shared Artist C
                    artistD // Unique Artist D
                )
        }

        // Remove one of the "Shared Artist C" items - artist should remain
        repository.remove(itemsC[0])
        reactive.advance()

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
        reactive.advance()

        eventually(1.seconds) {
            repository.artistsProperty.isEmpty() shouldBe true
        }
    }

    "FXAudioLibrary.createFromFile throws InvalidAudioFilePathException when file does not exist" {
        val missingPath = files.fileSystem.getPath("/no", "such", "file.mp3")
        val ex = shouldThrow<InvalidAudioFilePathException> { repository.createFromFile(missingPath) }
        ex.message shouldContain "does not exist"
    }

    "FXAudioLibrary.createFromFile throws InvalidAudioFilePathException when path is a directory" {
        val directoryPath = files.fileSystem.getPath("/").resolve("fx-some-directory")
        Files.createDirectories(directoryPath)
        val ex = shouldThrow<InvalidAudioFilePathException> { repository.createFromFile(directoryPath) }
        ex.message shouldContain "is not a regular file"
    }

    "FXAudioLibrary.createFromFile returns audio item seeded with metadataIO readTag result" {
        val audioFile = files.virtualAudioFile().next()
        val fxAudioItem = repository.createFromFile(audioFile)

        fxAudioItem.path shouldBe audioFile
        fxAudioItem.title.isNotEmpty() shouldBe true
    }
})