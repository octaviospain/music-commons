package net.transgressoft.commons.music.audio

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
    lateinit var audioRepository: AudioLibrary<AudioItem>

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
})