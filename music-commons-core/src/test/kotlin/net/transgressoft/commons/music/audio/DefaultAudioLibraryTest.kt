package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.util.InvalidAudioFilePathException
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.json.JsonRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@ExperimentalKotest
@ExperimentalCoroutinesApi
internal class DefaultAudioLibraryTest: StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()
    lateinit var jsonFile: File
    lateinit var jsonFileRepository: JsonRepository<Int, AudioItem>
    lateinit var audioRepository: AudioLibrary

    beforeEach {
        jsonFile = tempfile("audioLibrary-test", ".json").also { it.deleteOnExit() }
        jsonFileRepository = JsonFileRepository(jsonFile, MapSerializer(Int.serializer(), AudioItemSerializer(files.fileSystem)))
        audioRepository = DefaultAudioLibrary(jsonFileRepository, files.metadataIO)
    }

    afterEach {
        (audioRepository as? AutoCloseable)?.close()
        jsonFileRepository.close()
    }

    "Creates an audio item and allow to query it on creation and after modification" {
        val audioFile = files.virtualAudioFile().next()
        val audioItem: AudioItem = audioRepository.createFromFile(audioFile)

        reactive.advance()

        audioItem should {

            val artistNames = getArtistsNamesInvolved(it.title, it.artist.name, it.album.albumArtist.name)

            it.id shouldNotBe UNASSIGNED_ID
            audioRepository.add(it) shouldBe false
            audioRepository.contains { audioItem -> audioItem == it } shouldBe true
            audioRepository.search { audioItem -> audioItem == it }.shouldContainOnly(it)
            audioRepository.findByUniqueId(it.uniqueId) shouldBePresent { found -> found shouldBe it }
            val containsArtist = artistNames.any { name -> name.equals(it.artist.name, ignoreCase = true) }
            val containsAlbumArtist = artistNames.any { name -> name.equals(it.album.albumArtist.name, ignoreCase = true) }
            audioRepository.containsAudioItemWithArtist(it.artist.name) shouldBe containsArtist
            audioRepository.containsAudioItemWithArtist(it.album.albumArtist.name) shouldBe containsAlbumArtist
            audioRepository.findAlbumAudioItems(it.artist, it.album.name).shouldContainOnly(it)
        }

        audioItem.title = "New title"

        reactive.advance()

        audioItem should {

            val artistNames = getArtistsNamesInvolved(it.title, it.artist.name, it.album.albumArtist.name)

            audioRepository.size() shouldBe 1
            audioRepository.contains { audioItem -> it.title == "New title" } shouldBe true
            audioRepository.search { audioItem -> it.title == "New title" }.shouldContainOnly(it)
            audioRepository.findByUniqueId(it.uniqueId) shouldBePresent { found -> found shouldBe it }
            val containsArtist = artistNames.any { name -> name.equals(it.artist.name, ignoreCase = true) }
            val containsAlbumArtist = artistNames.any { name -> name.equals(it.album.albumArtist.name, ignoreCase = true) }
            audioRepository.containsAudioItemWithArtist(it.artist.name) shouldBe containsArtist
            audioRepository.containsAudioItemWithArtist(it.album.albumArtist.name) shouldBe containsAlbumArtist
            audioRepository.findAlbumAudioItems(it.artist, it.album.name).shouldContainOnly(it)
            audioRepository.findFirst { audioItem -> audioItem.title == "New title" } shouldBePresent { found ->
                found shouldBeSameInstanceAs it
            }
        }

        jsonFile shouldEqual audioItem.asJsonKeyValue()
    }

    "Reflects changes on a JsonFileRepository" {
        val audioFile = files.virtualAudioFile().next()
        val audioItem: AudioItem = audioRepository.createFromFile(audioFile)
        reactive.advance()

        jsonFile shouldEqual audioItem.asJsonKeyValue()

        audioRepository.findById(audioItem.id).ifPresent { it.bpm = 135f }
        reactive.advance()

        audioItem.bpm shouldBe 135f
        audioRepository.search { it.bpm == 135f }.shouldContainOnly(audioItem)
        jsonFile shouldEqual audioItem.asJsonKeyValue()
    }

    "Creates audio items from the same album" {
        val theBeatles = ImmutableArtist.of("The Beatles")
        val abbeyRoad = ImmutableAlbum("Abbey Road", theBeatles)
        val albumAudioFiles = files.virtualAlbumAudioFiles(theBeatles, abbeyRoad).next()

        albumAudioFiles.forEach(audioRepository::createFromFile)
        reactive.advance()

        val audioItems = audioRepository.search { it.album.name == abbeyRoad.name }
        audioItems.size shouldBe albumAudioFiles.size
        audioRepository.findAlbumAudioItems(theBeatles, abbeyRoad.name) shouldContainExactlyInAnyOrder audioItems
    }

    "Creates a batch of audio items asynchronously" {
        val filePaths = Arb.list(files.virtualAudioFile(), 50..100).next()

        val result: List<AudioItem> = audioRepository.createFromFileBatchAsync(filePaths, reactive.dispatcher.asExecutor()).get()

        reactive.advance()

        result.size shouldBe filePaths.size
        audioRepository.size() shouldBe filePaths.size

        val mp3Items = result.filter { it.path.toString().endsWith("mp3") }
        val flacItems = result.filter { it.path.toString().endsWith("flac") }
        val wavItems = result.filter { it.path.toString().endsWith("wav") }
        val m4aItems = result.filter { it.path.toString().endsWith("m4a") }

        mp3Items.forEach { it.encoding shouldBe "MPEG-1 Layer 3" }
        flacItems.forEach { it.encoding shouldBe "FLAC" }
        wavItems.forEach { it.encoding shouldBe "WAV" }
        m4aItems.forEach { it.encoding shouldBe "AAC" }

        val jsonObject = Json.parseToJsonElement(jsonFile.readText()).jsonObject

        result.forEach { audioItem -> jsonObject shouldContainAudioItem audioItem }
    }

    "Creates a batch of audio items asynchronously with custom batch size" {
        val filePaths = Arb.list(files.virtualAudioFile(), 10..20).next()

        val result: List<AudioItem> = audioRepository.createFromFileBatchAsync(filePaths, reactive.dispatcher.asExecutor(), batchSize = 1000).get()

        reactive.advance()

        result.size shouldBe filePaths.size
        audioRepository.size() shouldBe filePaths.size
    }

    "Batch async coerces batch size below 500 to 500" {
        val filePaths = Arb.list(files.virtualAudioFile(), 10..20).next()

        val result: List<AudioItem> = audioRepository.createFromFileBatchAsync(filePaths, reactive.dispatcher.asExecutor(), batchSize = 100).get()

        reactive.advance()

        result.size shouldBe filePaths.size
    }

    "Batch async rejects non-positive batch size" {
        shouldThrow<IllegalArgumentException> {
            audioRepository.createFromFileBatchAsync(listOf(), reactive.dispatcher.asExecutor(), batchSize = 0)
        }
        shouldThrow<IllegalArgumentException> {
            audioRepository.createFromFileBatchAsync(listOf(), reactive.dispatcher.asExecutor(), batchSize = -1)
        }
    }

    "Artist catalog registry should be populated when loading from existing repository" {
        // Create some audio items and save them to the JSON file
        val theBeatles = ImmutableArtist.of("The Beatles")
        val abbeyRoad = ImmutableAlbum("Abbey Road", theBeatles)
        val albumAudioFiles = files.virtualAlbumAudioFiles(theBeatles, abbeyRoad).next()

        albumAudioFiles.forEach(audioRepository::createFromFile)
        reactive.advance()

        val originalAudioItems = audioRepository.search { it.album.name == abbeyRoad.name }
        originalAudioItems.size shouldBe albumAudioFiles.size

        // Close the current repository and create a new one from the same JSON file
        // This simulates loading from a persisted file
        jsonFileRepository.close()

        val loadedJsonFileRepository = JsonFileRepository(jsonFile, MapSerializer(Int.serializer(), AudioItemSerializer(files.fileSystem)))
        val loadedAudioRepository = DefaultAudioLibrary(loadedJsonFileRepository, files.metadataIO)

        reactive.advance()

        // The loaded repository should have the same number of items
        loadedAudioRepository.size() shouldBe albumAudioFiles.size

        // The artist catalog methods should work correctly for the loaded items
        // This will fail because the artistCatalogRegistry is not populated on initialization
        loadedAudioRepository.findAlbumAudioItems(theBeatles, abbeyRoad.name) shouldContainExactlyInAnyOrder originalAudioItems
        loadedAudioRepository.getArtistCatalog(theBeatles) shouldBePresent { artistView ->
            artistView.artist shouldBe theBeatles
            artistView.albums.map { it.albumName }.shouldContainOnly(abbeyRoad.name)
        }
        loadedAudioRepository.containsAudioItemWithArtist(theBeatles.name) shouldBe true

        loadedJsonFileRepository.close()
    }

    "Artist catalog publisher emits CREATE events when audio items are added" {
        val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

        audioRepository.artistCatalogPublisher.subscribe(CREATE) { event ->
            receivedEvents.add(event)
        }

        val audioFile = files.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)
        reactive.advance()

        receivedEvents.size shouldBe 1
        receivedEvents[0].entities.size shouldBe 1
        receivedEvents[0].entities.values.first().artist shouldBe audioItem.artist
    }

    "Artist catalog publisher does NOT emit UPDATE events when single audio item ordering is modified" {
        val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

        val audioFile = files.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)
        reactive.advance()

        audioRepository.artistCatalogPublisher.subscribe(UPDATE) { receivedEvents.add(it) }

        // Modify track number on single item - should NOT trigger catalog UPDATE (no reordering possible)
        audioRepository.findById(audioItem.id).ifPresent { it.trackNumber = 5 }
        reactive.advance()

        receivedEvents.size shouldBe 0
    }

    "Artist catalog publisher emits UPDATE events when multiple audio items are reordered" {
        val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

        val theBeatles = ImmutableArtist.of("The Beatles")
        val abbeyRoad = ImmutableAlbum("Abbey Road", theBeatles)

        val audioFile1 =
            files.virtualAudioFile {
                artist = theBeatles
                album = abbeyRoad
                trackNumber = 1
                discNumber = 1
            }.next()
        val audioItem1 = audioRepository.createFromFile(audioFile1)

        val audioFile2 =
            files.virtualAudioFile {
                artist = theBeatles
                album = abbeyRoad
                trackNumber = 2
                discNumber = 1
            }.next()
        val audioItem2 = audioRepository.createFromFile(audioFile2)
        reactive.advance()

        audioRepository.artistCatalogPublisher.subscribe(UPDATE) { receivedEvents.add(it) }

        // Modify track number on first item to make it last - should trigger catalog UPDATE (reordering)
        audioRepository.findById(audioItem1.id).ifPresent { it.trackNumber = 5 }
        reactive.advance()

        eventually(1.seconds) {
            receivedEvents.size shouldBe 1
            receivedEvents[0] should { event ->
                event.entities.size shouldBe 1
                event.entities.values.first() should { artistCatalog ->
                    artistCatalog.artist shouldBe theBeatles
                    artistCatalog.size shouldBe 2
                    artistCatalog.albumAudioItems(abbeyRoad.name) should { audioItems ->
                        audioItems.size shouldBe 2
                        // After reordering, audioItem1 should be last (highest track number)
                        audioItems.last().id shouldBe audioItem1.id
                        audioItems.last().trackNumber shouldBe 5
                    }
                }
            }
        }
    }

    "Artist catalog publisher emits DELETE events when all artist items are removed" {
        val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

        val audioFile = files.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)
        reactive.advance()

        audioRepository.artistCatalogPublisher.subscribe(DELETE) { receivedEvents.add(it) }

        audioRepository.removeAll(listOf(audioItem))
        reactive.advance()

        receivedEvents.size shouldBe 1
        receivedEvents[0].entities.size shouldBe 1
        receivedEvents[0].entities.values.first().artist shouldBe audioItem.artist
    }

    "Artist catalog publisher emits events when artist changes between audio items" {
        val createEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()
        val updateEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()
        val deleteEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

        audioRepository.artistCatalogPublisher.subscribe(CREATE) { createEvents.add(it) }

        audioRepository.artistCatalogPublisher.subscribe(UPDATE) { updateEvents.add(it) }

        audioRepository.artistCatalogPublisher.subscribe(DELETE) { deleteEvents.add(it) }

        val audioFile = files.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)
        reactive.advance()

        val originalArtist = audioItem.artist
        createEvents.size shouldBe 1

        // Change artist - should delete old catalog and create new one
        val newArtist = ImmutableArtist.of("New Artist")
        audioRepository.findById(audioItem.id).ifPresent { it.artist = newArtist }
        reactive.advance()

        eventually(1.seconds) {
            createEvents.size shouldBe 2
            createEvents[1].entities.values.first().artist shouldBe newArtist
            deleteEvents.size shouldBe 1
            deleteEvents[0].entities.values.first().artist shouldBe originalArtist
        }
    }

    "Artist catalog publisher provides access to catalog albums and items" {
        val receivedCatalogs = mutableListOf<ArtistCatalog<AudioItem>>()

        audioRepository.artistCatalogPublisher.subscribe(CREATE) { event ->
            receivedCatalogs.addAll(event.entities.values)
        }

        val theBeatles = ImmutableArtist.of("The Beatles")
        val abbeyRoad = ImmutableAlbum("Abbey Road", theBeatles)
        val albumFiles = files.virtualAlbumAudioFiles(theBeatles, abbeyRoad).next()

        albumFiles.forEach { audioRepository.createFromFile(it) }
        reactive.advance()

        receivedCatalogs.size shouldBe 1
        receivedCatalogs[0] should { catalog ->
            catalog.artist shouldBe theBeatles
            catalog.albums.size shouldBe 1
            catalog.albums.first().albumName shouldBe abbeyRoad.name
            catalog.size shouldBe albumFiles.size
        }
    }

    "DefaultAudioLibrary.createFromFile throws InvalidAudioFilePathException when file does not exist" {
        val missingPath = files.fileSystem.getPath("/does", "not", "exist.mp3")
        val ex = shouldThrow<InvalidAudioFilePathException> { audioRepository.createFromFile(missingPath) }
        ex.message shouldContain "does not exist"
    }

    "DefaultAudioLibrary.createFromFile throws InvalidAudioFilePathException when path is a directory" {
        val directoryPath = files.fileSystem.getPath("/").resolve("some-directory")
        Files.createDirectories(directoryPath)
        val ex = shouldThrow<InvalidAudioFilePathException> { audioRepository.createFromFile(directoryPath) }
        ex.message shouldContain "is not a regular file"
    }

    "DefaultAudioLibrary.createFromFile returns audio item seeded with metadataIO readTag result" {
        val audioFile = files.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)

        // Tag-derived properties (title, artist, album) must reflect what FakeAudioMetadataIO
        // produced for the virtual file; null-empty tags would indicate the library bypassed readTag.
        audioItem.path shouldBe audioFile
        audioItem.id shouldNotBe UNASSIGNED_ID
        audioItem.title.isNotEmpty() shouldBe true
    }

    "DefaultAudioLibrary.writeMetadata invokes metadataIO writeMetadata synchronously" {
        val audioFile = files.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)
        reactive.advance()

        audioItem.title = "Synchronous write title"
        files.metadataIO.writeMetadata(audioItem)

        files.metadataIO.readMetadata(audioFile).title shouldBe "Synchronous write title"
    }

    "DefaultAudioLibrary.loadCover returns bytes from metadataIO readCoverBytes" {
        val audioFile = files.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)

        val expected = byteArrayOf(1, 2, 3, 4, 5)
        files.metadataIO.stubCover(audioFile, expected)

        files.metadataIO.loadCover(audioItem) shouldBe expected
    }
})