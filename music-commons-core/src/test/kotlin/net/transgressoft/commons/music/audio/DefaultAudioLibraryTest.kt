package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAlbumAudioFiles
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.persistence.json.JsonFileRepository
import net.transgressoft.commons.persistence.json.JsonRepository
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.mockk.unmockkAll
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@ExperimentalKotest
@ExperimentalCoroutinesApi
internal class DefaultAudioLibraryTest: StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var jsonFileRepository: JsonRepository<Int, AudioItem>
    lateinit var audioRepository: AudioLibrary<AudioItem, ArtistCatalog<AudioItem>>

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        jsonFile = tempfile("audioLibrary-test", ".json").also { it.deleteOnExit() }
        jsonFileRepository = JsonFileRepository(jsonFile, AudioItemMapSerializer)
        audioRepository = DefaultAudioLibrary(jsonFileRepository)
    }

    afterEach {
        jsonFileRepository.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
        unmockkAll()
    }

    "Creates an audio item and allow to query it on creation and after modification" {
        val audioFile = Arb.virtualAudioFile().next()
        val audioItem: AudioItem = audioRepository.createFromFile(audioFile)

        testDispatcher.scheduler.advanceUntilIdle()

        audioItem should {

            val artistNames = AudioUtils.getArtistsNamesInvolved(it.title, it.artist.name, it.album.albumArtist.name)

            it.id shouldNotBe UNASSIGNED_ID
            audioRepository.add(it) shouldBe false
            audioRepository.addOrReplace(it) shouldBe false
            audioRepository.contains { audioItem -> audioItem == it } shouldBe true
            audioRepository.search { audioItem -> audioItem == it }.shouldContainOnly(it)
            audioRepository.findByUniqueId(it.uniqueId) shouldBePresent { found -> found shouldBe it }
            audioRepository.containsAudioItemWithArtist(it.artist.name) shouldBe artistNames.contains(it.artist.name)
            audioRepository.containsAudioItemWithArtist(it.album.albumArtist.name) shouldBe artistNames.contains(it.album.albumArtist.name)
            audioRepository.findAlbumAudioItems(it.artist, it.album.name).shouldContainOnly(it)
        }

        audioItem.title = "New title"

        testDispatcher.scheduler.advanceUntilIdle()

        audioItem should {

            val artistNames = AudioUtils.getArtistsNamesInvolved(it.title, it.artist.name, it.album.albumArtist.name)

            audioRepository.size() shouldBe 1
            audioRepository.contains { audioItem -> it.title == "New title" } shouldBe true
            audioRepository.search { audioItem -> it.title == "New title" }.shouldContainOnly(it)
            audioRepository.findByUniqueId(it.uniqueId) shouldBePresent { found -> found shouldBe it }
            audioRepository.containsAudioItemWithArtist(it.artist.name) shouldBe artistNames.contains(it.artist.name)
            audioRepository.containsAudioItemWithArtist(it.album.albumArtist.name) shouldBe artistNames.contains(it.album.albumArtist.name)
            audioRepository.findAlbumAudioItems(it.artist, it.album.name).shouldContainOnly(it)
            audioRepository.findFirst { audioItem -> audioItem.title == "New title" } shouldBePresent { found ->
                found shouldBeSameInstanceAs it
            }
        }

        jsonFile shouldEqual audioItem.asJsonKeyValue()
    }

    "Reflects changes on a JsonFileRepository" {
        val audioFile = Arb.virtualAudioFile().next()
        val audioItem: AudioItem = audioRepository.createFromFile(audioFile)
        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile shouldEqual audioItem.asJsonKeyValue()

        audioRepository.runForSingle(audioItem.id) { it.bpm = 135f }
        testDispatcher.scheduler.advanceUntilIdle()

        audioItem.bpm shouldBe 135f
        audioRepository.search { it.bpm == 135f }.shouldContainOnly(audioItem)
        jsonFile shouldEqual audioItem.asJsonKeyValue()
    }

    "Creates audio items from the same album" {
        val theBeatles = ImmutableArtist.of("The Beatles")
        val abbeyRoad = ImmutableAlbum("Abbey Road", theBeatles)
        val albumAudioFiles = Arb.virtualAlbumAudioFiles(theBeatles, abbeyRoad).next()

        albumAudioFiles.forEach(audioRepository::createFromFile)
        testDispatcher.scheduler.advanceUntilIdle()

        val audioItems = audioRepository.search { it.album.name == abbeyRoad.name }
        audioItems.size shouldBe albumAudioFiles.size
        audioRepository.findAlbumAudioItems(theBeatles, abbeyRoad.name) shouldContainExactlyInAnyOrder audioItems
    }

    "Creates a batch of audio items asynchronously" {
        val filePaths = Arb.list(Arb.virtualAudioFile(), 50..100).next()

        val result: List<AudioItem> = audioRepository.createFromFileBatchAsync(filePaths, testDispatcher.asExecutor()).get()

        testDispatcher.scheduler.advanceUntilIdle()

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

    "Artist catalog registry should be populated when loading from existing repository" {
        // Create some audio items and save them to the JSON file
        val theBeatles = ImmutableArtist.of("The Beatles")
        val abbeyRoad = ImmutableAlbum("Abbey Road", theBeatles)
        val albumAudioFiles = Arb.virtualAlbumAudioFiles(theBeatles, abbeyRoad).next()

        albumAudioFiles.forEach(audioRepository::createFromFile)
        testDispatcher.scheduler.advanceUntilIdle()

        val originalAudioItems = audioRepository.search { it.album.name == abbeyRoad.name }
        originalAudioItems.size shouldBe albumAudioFiles.size

        // Close the current repository and create a new one from the same JSON file
        // This simulates loading from a persisted file
        jsonFileRepository.close()

        val loadedJsonFileRepository = JsonFileRepository(jsonFile, AudioItemMapSerializer)
        val loadedAudioRepository = DefaultAudioLibrary(loadedJsonFileRepository)

        testDispatcher.scheduler.advanceUntilIdle()

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

        val audioFile = Arb.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)
        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        receivedEvents[0].entities.size shouldBe 1
        receivedEvents[0].entities.values.first().artist shouldBe audioItem.artist
    }

    "Artist catalog publisher does NOT emit UPDATE events when single audio item ordering is modified" {
        val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

        val audioFile = Arb.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)
        testDispatcher.scheduler.advanceUntilIdle()

        audioRepository.artistCatalogPublisher.subscribe(UPDATE) { receivedEvents.add(it) }

        // Modify track number on single item - should NOT trigger catalog UPDATE (no reordering possible)
        audioRepository.runForSingle(audioItem.id) { it.trackNumber = 5 }
        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 0
    }

    "Artist catalog publisher emits UPDATE events when multiple audio items are reordered" {
        val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

        val theBeatles = ImmutableArtist.of("The Beatles")
        val abbeyRoad = ImmutableAlbum("Abbey Road", theBeatles)

        val audioFile1 =
            Arb.virtualAudioFile {
                artist = theBeatles
                album = abbeyRoad
                trackNumber = 1
                discNumber = 1
            }.next()
        val audioItem1 = audioRepository.createFromFile(audioFile1)

        val audioFile2 =
            Arb.virtualAudioFile {
                artist = theBeatles
                album = abbeyRoad
                trackNumber = 2
                discNumber = 1
            }.next()
        val audioItem2 = audioRepository.createFromFile(audioFile2)
        testDispatcher.scheduler.advanceUntilIdle()

        audioRepository.artistCatalogPublisher.subscribe(UPDATE) { receivedEvents.add(it) }

        // Modify track number on first item to make it last - should trigger catalog UPDATE (reordering)
        audioRepository.runForSingle(audioItem1.id) { it.trackNumber = 5 }
        testDispatcher.scheduler.advanceUntilIdle()

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

    "Artist catalog publisher emits DELETE events when all artist items are removed" {
        val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

        val audioFile = Arb.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)
        testDispatcher.scheduler.advanceUntilIdle()

        audioRepository.artistCatalogPublisher.subscribe(DELETE) { receivedEvents.add(it) }

        audioRepository.removeAll(listOf(audioItem))
        testDispatcher.scheduler.advanceUntilIdle()

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

        val audioFile = Arb.virtualAudioFile().next()
        val audioItem = audioRepository.createFromFile(audioFile)
        testDispatcher.scheduler.advanceUntilIdle()

        val originalArtist = audioItem.artist
        createEvents.size shouldBe 1

        // Change artist - should delete old catalog and create new one
        val newArtist = ImmutableArtist.of("New Artist")
        audioRepository.runForSingle(audioItem.id) { it.artist = newArtist }
        testDispatcher.scheduler.advanceUntilIdle()

        createEvents.size shouldBe 2
        createEvents[1].entities.values.first().artist shouldBe newArtist
        deleteEvents.size shouldBe 1
        deleteEvents[0].entities.values.first().artist shouldBe originalArtist
    }

    "Artist catalog publisher provides access to catalog albums and items" {
        val receivedCatalogs = mutableListOf<ArtistCatalog<AudioItem>>()

        audioRepository.artistCatalogPublisher.subscribe(CREATE) { event ->
            receivedCatalogs.addAll(event.entities.values)
        }

        val theBeatles = ImmutableArtist.of("The Beatles")
        val abbeyRoad = ImmutableAlbum("Abbey Road", theBeatles)
        val albumFiles = Arb.virtualAlbumAudioFiles(theBeatles, abbeyRoad).next()

        albumFiles.forEach { audioRepository.createFromFile(it) }
        testDispatcher.scheduler.advanceUntilIdle()

        receivedCatalogs.size shouldBe 1
        receivedCatalogs[0] should { catalog ->
            catalog.artist shouldBe theBeatles
            catalog.albums.size shouldBe 1
            catalog.albums.first().albumName shouldBe abbeyRoad.name
            catalog.size shouldBe albumFiles.size
        }
    }
})