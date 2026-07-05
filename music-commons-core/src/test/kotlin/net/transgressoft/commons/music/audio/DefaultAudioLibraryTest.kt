package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
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
import io.kotest.datatest.withData
import io.kotest.engine.names.WithDataTestName
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldBeEmpty
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
import kotlinx.serialization.json.Json

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
        jsonFileRepository = JsonFileRepository(jsonFile, AudioItemMapSerializer)
        audioRepository = DefaultAudioLibrary(jsonFileRepository, files.metadataIO)
    }

    afterEach {
        (audioRepository as? AutoCloseable)?.close()
        jsonFileRepository.close()
    }

    // Reload the persisted JSON map directly through the public serializer to assert that the
    // repository actually wrote the item to disk, without coupling to the on-disk field layout.
    // Paths round-trip to the default filesystem on load (Jimfs provider identity is not
    // preserved), so fidelity is asserted on id and metadata rather than path-bearing equality.
    fun reloadPersistedItem(id: Int): AudioItem? =
        Json.decodeFromString(AudioItemMapSerializer, jsonFile.readText())[id]

    infix fun AudioItem?.reloadedMatches(original: AudioItem) {
        this shouldNotBe null
        this!!.id shouldBe original.id
        title shouldBe original.title
        artist.name shouldBe original.artist.name
        album.name shouldBe original.album.name
        genres shouldBe original.genres
    }

    "Creates an audio item and allow to query it on creation and after modification" {
        // Deterministic artist and title so the catalog-key Artist (name-only, no country code) is predictable
        val itemArtist = Artist.of("Portishead")
        val itemAlbum = AlbumDetails("Dummy", itemArtist)
        val audioFile =
            files.virtualAudioFile {
                artist = itemArtist
                album = itemAlbum
                title = "Glory Box"
            }.next()
        val audioItem: AudioItem = audioRepository.createFromFile(audioFile)

        reactive.advance()

        audioItem should {
            it.id shouldNotBe UNASSIGNED_ID
            audioRepository.add(it) shouldBe false
            audioRepository.contains { audioItem -> audioItem == it } shouldBe true
            audioRepository.search { audioItem -> audioItem == it }.shouldContainOnly(it)
            audioRepository.findByUniqueId(it.uniqueId) shouldBePresent { found -> found shouldBe it }
            audioRepository.containsAudioItemWithArtist(it.artist.name) shouldBe true
            audioRepository.containsAudioItemWithArtist(it.album.albumArtist.name) shouldBe true
            // Catalog lookup uses Artist derived from name (no country code)
            audioRepository.findAlbumAudioItems(Artist.of(it.artist.name), it.album.name).shouldContainOnly(it)
        }

        audioItem.title = "New title"

        reactive.advance()

        audioItem should {
            audioRepository.size() shouldBe 1
            audioRepository.contains { audioItem -> audioItem.title == "New title" } shouldBe true
            audioRepository.search { audioItem -> audioItem.title == "New title" }.shouldContainOnly(it)
            audioRepository.findByUniqueId(it.uniqueId) shouldBePresent { found -> found shouldBe it }
            audioRepository.containsAudioItemWithArtist(it.artist.name) shouldBe true
            audioRepository.containsAudioItemWithArtist(it.album.albumArtist.name) shouldBe true
            audioRepository.findAlbumAudioItems(Artist.of(it.artist.name), it.album.name).shouldContainOnly(it)
            audioRepository.findFirst { audioItem -> audioItem.title == "New title" } shouldBePresent { found ->
                found shouldBeSameInstanceAs it
            }
        }

        reloadPersistedItem(audioItem.id) reloadedMatches audioItem
    }

    "Reflects changes on a JsonFileRepository" {
        val audioFile = files.virtualAudioFile().next()
        val audioItem: AudioItem = audioRepository.createFromFile(audioFile)
        reactive.advance()

        reloadPersistedItem(audioItem.id) reloadedMatches audioItem

        audioRepository.findById(audioItem.id).ifPresent { it.bpm = 135f }
        reactive.advance()

        audioItem.bpm shouldBe 135f
        audioRepository.search { it.bpm == 135f }.shouldContainOnly(audioItem)
        reloadPersistedItem(audioItem.id)?.bpm shouldBe 135f
    }

    "Creates audio items from the same album" {
        val theBeatles = Artist.of("The Beatles")
        val abbeyRoad = AlbumDetails("Abbey Road", theBeatles)
        val albumAudioFiles = files.virtualAlbumAudioFiles(theBeatles, abbeyRoad).next()

        albumAudioFiles.forEach(audioRepository::createFromFile)
        reactive.advance()

        val audioItems = audioRepository.search { it.album.name == abbeyRoad.name }
        audioItems.size shouldBe albumAudioFiles.size
        audioRepository.findAlbumAudioItems(theBeatles, abbeyRoad.name) shouldContainExactlyInAnyOrder audioItems
    }

    "Creates a batch of audio items asynchronously with tag-derived encoding and persistence" {
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

        val persisted = Json.decodeFromString(AudioItemMapSerializer, jsonFile.readText())

        persisted.keys shouldContainExactlyInAnyOrder result.map { it.id }
    }

    // Every accepted batch size must create the full set of items: the default (no explicit size),
    // an oversized value larger than the input, and a value below the 500 floor that is coerced up.
    withData(
        nameFn = { "batch async creates all items with batch size ${it ?: "default"}" },
        null,
        1000,
        100
    ) { batchSize ->
        val filePaths = Arb.list(files.virtualAudioFile(), 10..20).next()

        val result: List<AudioItem> =
            if (batchSize == null) {
                audioRepository.createFromFileBatchAsync(filePaths, reactive.dispatcher.asExecutor()).get()
            } else {
                audioRepository.createFromFileBatchAsync(filePaths, reactive.dispatcher.asExecutor(), batchSize = batchSize).get()
            }

        reactive.advance()

        result.size shouldBe filePaths.size
        audioRepository.size() shouldBe filePaths.size
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
        val theBeatles = Artist.of("The Beatles")
        val abbeyRoad = AlbumDetails("Abbey Road", theBeatles)
        val albumAudioFiles = files.virtualAlbumAudioFiles(theBeatles, abbeyRoad).next()

        albumAudioFiles.forEach(audioRepository::createFromFile)
        reactive.advance()

        val originalAudioItems = audioRepository.search { it.album.name == abbeyRoad.name }
        originalAudioItems.size shouldBe albumAudioFiles.size

        // Close the current repository and create a new one from the same JSON file
        // This simulates loading from a persisted file
        jsonFileRepository.close()

        val loadedJsonFileRepository = JsonFileRepository(jsonFile, AudioItemMapSerializer)
        val loadedAudioRepository = DefaultAudioLibrary(loadedJsonFileRepository, files.metadataIO)

        reactive.advance()

        // The loaded repository should have the same number of items
        loadedAudioRepository.size() shouldBe albumAudioFiles.size

        // The artist catalog methods should work correctly for the loaded items.
        // Compare by stable id: paths round-trip to the default filesystem on load (the Jimfs
        // provider identity is not preserved), so full-object equality on the in-memory Jimfs
        // originals would not hold; per-row path fidelity is covered by the persistence round-trip tests.
        loadedAudioRepository.findAlbumAudioItems(theBeatles, abbeyRoad.name)
            .map { it.id } shouldContainExactlyInAnyOrder originalAudioItems.map { it.id }
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

        // Deterministic title and matching albumArtist keep artistsInvolved == {Portishead},
        // so exactly one CREATE event fires for the single artist bucket
        val artist = Artist.of("Portishead")
        val audioFile =
            files.virtualAudioFile {
                this.artist = artist
                album = AlbumDetails("Dummy", artist)
                title = "Sour Times"
            }.next()
        val audioItem = audioRepository.createFromFile(audioFile)
        reactive.advance()

        eventually(2.seconds) {
            receivedEvents.size shouldBe 1
            receivedEvents[0].entities.size shouldBe 1
            receivedEvents[0].entities.values.first().artist.name shouldBe audioItem.artist.name
        }
    }

    // Within-bucket property changes (e.g. trackNumber) do not change the projection key (artist),
    // so no bucket-change notification fires and no catalog UPDATE event is emitted — whether one
    // item is edited or one of several in the same bucket is reordered. Deterministic titles prevent
    // random separator tokens from inflating artistsInvolved. Each row seeds its items, subscribes
    // UPDATE, mutates a track number, and asserts no event surfaced (advance() drains the test
    // dispatcher to idle, so any catalog UPDATE would already have been delivered — a deterministic
    // negative). The follow-up verifies the catalog still holds every seeded item.
    data class NoUpdateCase(
        val scenarioName: String,
        val seed: (VirtualFiles, AudioLibrary) -> AudioItem,
        val verifyCatalog: (AudioLibrary) -> Unit
    ) : WithDataTestName {
        override fun dataTestName() = scenarioName
    }

    withData(
        NoUpdateCase(
            "single audio item ordering is modified",
            seed = { files, library ->
                val artist = Artist.of("Portishead")
                library.createFromFile(
                    files.virtualAudioFile {
                        this.artist = artist
                        album = AlbumDetails("Dummy", artist)
                        title = "Sour Times"
                    }.next()
                )
            },
            verifyCatalog = {}
        ),
        NoUpdateCase(
            "one of multiple audio items in a bucket is reordered",
            seed = { files, library ->
                val theBeatles = Artist.of("The Beatles")
                val abbeyRoad = AlbumDetails("Abbey Road", theBeatles)
                val first =
                    library.createFromFile(
                        files.virtualAudioFile {
                            artist = theBeatles
                            album = abbeyRoad
                            title = "Come Together"
                            trackNumber = 1
                            discNumber = 1
                        }.next()
                    )
                library.createFromFile(
                    files.virtualAudioFile {
                        artist = theBeatles
                        album = abbeyRoad
                        title = "Something"
                        trackNumber = 2
                        discNumber = 1
                    }.next()
                )
                first
            },
            verifyCatalog = { library ->
                // Both items still accessible in the catalog
                library.getArtistCatalog(Artist.of("The Beatles")) shouldBePresent { catalog ->
                    catalog.size shouldBe 2
                }
            }
        )
    ) { case ->
        val target = case.seed(files, audioRepository)
        reactive.advance()

        val receivedEvents = audioRepository.artistCatalogPublisher.collect(UPDATE)

        // Modify track number - does NOT trigger a catalog UPDATE for within-bucket changes
        audioRepository.findById(target.id).ifPresent { it.trackNumber = 5 }
        reactive.advance()

        receivedEvents.shouldBeEmpty()
        case.verifyCatalog(audioRepository)
    }

    "Artist catalog publisher emits DELETE events when all artist items are removed" {
        val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

        // Deterministic title and matching albumArtist keep artistsInvolved == {Portishead}
        // so exactly one DELETE event fires when the single item is removed
        val artist = Artist.of("Portishead")
        val audioFile =
            files.virtualAudioFile {
                this.artist = artist
                album = AlbumDetails("Dummy", artist)
                title = "Sour Times"
            }.next()
        val audioItem = audioRepository.createFromFile(audioFile)
        reactive.advance()

        audioRepository.artistCatalogPublisher.subscribe(DELETE) { receivedEvents.add(it) }

        audioRepository.removeAll(listOf(audioItem))
        reactive.advance()

        eventually(2.seconds) {
            receivedEvents.size shouldBe 1
            receivedEvents[0].entities.size shouldBe 1
            receivedEvents[0].entities.values.first().artist.name shouldBe audioItem.artist.name
        }
    }

    "Artist catalog publisher emits events when artist changes between audio items" {
        val createEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()
        val updateEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()
        val deleteEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

        audioRepository.artistCatalogPublisher.subscribe(CREATE) { createEvents.add(it) }
        audioRepository.artistCatalogPublisher.subscribe(UPDATE) { updateEvents.add(it) }
        audioRepository.artistCatalogPublisher.subscribe(DELETE) { deleteEvents.add(it) }

        // Deterministic title and matching albumArtist keep artistsInvolved == {Portishead},
        // so exactly one CREATE fires on add and one DELETE fires when the artist is replaced
        val originalArtistObj = Artist.of("Portishead")
        val audioFile =
            files.virtualAudioFile {
                artist = originalArtistObj
                album = AlbumDetails("Dummy", originalArtistObj)
                title = "Sour Times"
            }.next()
        val audioItem = audioRepository.createFromFile(audioFile)
        reactive.advance()

        eventually(2.seconds) { createEvents.size shouldBe 1 }

        // Change artist and album artist so originalArtist fully leaves artistsInvolved
        val newArtist = Artist.of("Massive Attack")
        audioRepository.findById(audioItem.id).ifPresent {
            it.artist = newArtist
            it.album = AlbumDetails("Blue Lines", newArtist)
        }
        reactive.advance()

        eventually(1.seconds) {
            // One new CREATE for "Massive Attack", one DELETE for "Portishead"
            createEvents.any { event -> event.entities.values.any { it.artist.name == "Massive Attack" } } shouldBe true
            deleteEvents.any { event -> event.entities.values.any { it.artist.name == "Portishead" } } shouldBe true
        }
    }

    "Artist catalog publisher provides access to catalog albums and items" {
        val theBeatles = Artist.of("The Beatles")
        val abbeyRoad = AlbumDetails("Abbey Road", theBeatles)
        val albumFiles = files.virtualAlbumAudioFiles(theBeatles, abbeyRoad).next()

        albumFiles.forEach { audioRepository.createFromFile(it) }
        reactive.advance()

        // The first item triggers a CREATE event; subsequent items for the same artist trigger UPDATE.
        // After all additions, query the catalog directly to verify full contents.
        eventually(2.seconds) {
            audioRepository.getArtistCatalog(theBeatles) shouldBePresent { catalog ->
                catalog.artist shouldBe theBeatles
                catalog.albums.size shouldBe 1
                catalog.albums.first().albumName shouldBe abbeyRoad.name
                catalog.size shouldBe albumFiles.size
            }
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
})