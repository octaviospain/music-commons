package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAlbumAudioItems
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryMp3File
import net.transgressoft.commons.music.audio.AudioItemTestUtil.asJsonKeyValue
import net.transgressoft.commons.music.audio.AudioItemTestUtil.createMockedAudioFilePaths
import net.transgressoft.commons.music.audio.AudioItemTestUtil.shouldContainAudioItem
import io.kotest.assertions.json.shouldEqualJson
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
import io.kotest.property.PropTestConfig
import io.kotest.property.PropTestListener
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@ExperimentalKotest
@ExperimentalCoroutinesApi
internal class AudioItemJsonRepositoryTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var audioRepository: AudioRepository

    beforeSpec {
        ReactiveScope.setDefaultFlowScope(testScope)
        ReactiveScope.setDefaultIoScope(testScope)
    }

    fun setup() {
        jsonFile = tempfile("audioItemRepository-test", ".json").also { it.deleteOnExit() }
        audioRepository = AudioItemJsonRepository("AudioRepo", jsonFile)
    }

    beforeEach { setup() }

    val setupListener =
        object: PropTestListener {
            override suspend fun beforeTest() {
                setup()
            }
        }

    afterEach {
        audioRepository.close()
    }

    afterSpec {
        ReactiveScope.setDefaultFlowScope(CoroutineScope(Dispatchers.Default.limitedParallelism(4) + SupervisorJob()))
        ReactiveScope.setDefaultIoScope(CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob()))
    }

    "should create an audio item and allow to query it on creation and after modification" {
        val audioItem: AudioItem =
            audioRepository.createFromFile(arbitraryMp3File.next().toPath()).apply {
                should {
                    it.id shouldNotBe UNASSIGNED_ID
                    audioRepository.add(it) shouldBe false
                    audioRepository.addOrReplace(it) shouldBe false
                    audioRepository.contains { audioItem -> audioItem == it } shouldBe true
                    audioRepository.search { audioItem -> audioItem == it }.shouldContainOnly(it)
                    audioRepository.findByUniqueId(it.uniqueId) shouldBePresent { found -> found shouldBe it }
                    audioRepository.containsAudioItemWithArtist(it.artist.name) shouldBe true
                    audioRepository.containsAudioItemWithArtist(it.album.albumArtist.name) shouldBe true
                    audioRepository.findAlbumAudioItems(it.artist, it.album.name).shouldContainOnly(it)
                }
            }
        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText().shouldEqualJson(audioItem.asJsonKeyValue())

        audioItem.title = "New title"
        should {
            audioRepository.size() shouldBe 1
            audioRepository.contains { audioItem -> audioItem.title == "New title" } shouldBe true
            audioRepository.search { audioItem -> audioItem.title == "New title" }.shouldContainOnly(audioItem)
            audioRepository.findByUniqueId(audioItem.uniqueId) shouldBePresent { found -> found shouldBe audioItem }
            audioRepository.containsAudioItemWithArtist(audioItem.artist.name) shouldBe true
            audioRepository.containsAudioItemWithArtist(audioItem.album.albumArtist.name) shouldBe true
            audioRepository.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)
            audioRepository.findFirst { audioItem -> audioItem.title == "New title" } shouldBePresent {
                audioItem shouldBeSameInstanceAs audioItem
            }
        }
        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText().shouldEqualJson(audioItem.asJsonKeyValue())
    }

    "should serialize itself when entity is modified during an action on the repository" {
        val audioItem: AudioItem = audioRepository.createFromFile(arbitraryMp3File.next().toPath())
        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText().shouldEqualJson(audioItem.asJsonKeyValue())

        audioRepository.runForSingle(audioItem.id) { it.bpm = 135f }
        testDispatcher.scheduler.advanceUntilIdle()

        audioItem.bpm shouldBe 135f
        audioRepository.search { it.bpm == 135f }.shouldContainOnly(audioItem)
        jsonFile.readText().shouldEqualJson(audioItem.asJsonKeyValue())
    }

    "should create audio items from the same album and reflect changes in the repository" {
        checkAll(10, PropTestConfig(listeners = listOf(setupListener)), arbitraryAlbumAudioItems()) { testAlbumAudioItems ->
            val expectedArtist = testAlbumAudioItems[0].artist
            val expectedAlbum = testAlbumAudioItems[0].album

            testAlbumAudioItems.forEach { audioRepository.createFromFile(it.path) }
            testDispatcher.scheduler.advanceUntilIdle()

            val currentAlbumItems = audioRepository.search { it.album.name == expectedAlbum.name }
            currentAlbumItems.size shouldBe testAlbumAudioItems.size
            audioRepository.findAlbumAudioItems(expectedArtist, expectedAlbum.name) shouldContainExactlyInAnyOrder currentAlbumItems
        }
    }

    "should create audio items asynchronously" {
        mockkStatic("kotlin.io.FilesKt__FileReadWriteKt")
        mockkStatic("kotlin.io.FilesKt__UtilsKt")
        mockkStatic("org.jaudiotagger.audio.AudioFileIO")

        val totalFiles = 10
        val filePaths = createMockedAudioFilePaths(totalFiles)

        val result: CompletableFuture<List<AudioItem>> = audioRepository.createFromFileBatchAsync(filePaths, testDispatcher.asExecutor())

        testDispatcher.scheduler.advanceUntilIdle()

        result.get().size shouldBe totalFiles

        audioRepository.size() shouldBe totalFiles

        val mp3Items = result.get().filter { it.path.toString().endsWith("mp3") }
        val flacItems = result.get().filter { it.path.toString().endsWith("flac") }
        val wavItems = result.get().filter { it.path.toString().endsWith("wav") }
        val m4aItems = result.get().filter { it.path.toString().endsWith("m4a") }

        mp3Items.forEach { it.encoding shouldBe "MPEG-1 Layer 3" }
        flacItems.forEach { it.encoding shouldBe "FLAC" }
        wavItems.forEach { it.encoding shouldBe "WAV" }
        m4aItems.forEach { it.encoding shouldBe "AAC" }

        val jsonObject = Json.parseToJsonElement(jsonFile.readText()).jsonObject

        result.get().forEach { audioItem -> jsonObject shouldContainAudioItem audioItem }

        unmockkAll()
    }
})