package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAlbumAudioItems
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryMp3File
import net.transgressoft.commons.music.audio.AudioItemTestUtil.asJsonKeyValue
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
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
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalKotest::class)
internal class AudioItemJsonRepositoryTest : StringSpec({

    lateinit var jsonFile: File
    lateinit var audioRepository: AudioRepository

    fun beforeEach() {
        jsonFile = tempfile("audioItemRepository-test", ".json").also { it.deleteOnExit() }
        audioRepository = AudioItemJsonRepository("AudioRepo", jsonFile)
    }

    beforeEach { beforeEach() }

    "should create an audio item and allow to query it on creation and after modification" {
        val audioItem: AudioItem = audioRepository.createFromFile(arbitraryMp3File.next().toPath()).apply {
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

        eventually(100.milliseconds) { jsonFile.readText().shouldEqualJson(audioItem.asJsonKeyValue()) }

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

        eventually(100.milliseconds) { jsonFile.readText().shouldEqualJson(audioItem.asJsonKeyValue()) }
    }

    "should serialize itself when entity is modified during an action on the repository" {
        val audioItem: AudioItem = audioRepository.createFromFile(arbitraryMp3File.next().toPath())

        eventually(100.milliseconds) { jsonFile.readText().shouldEqualJson(audioItem.asJsonKeyValue()) }

        audioRepository.runForSingle(audioItem.id) { it.bpm = 135f }

        eventually(100.milliseconds) {
            audioItem.bpm shouldBe 135f
            audioRepository.search { it.bpm == 135f }.shouldContainOnly(audioItem)
            jsonFile.readText().shouldEqualJson(audioItem.asJsonKeyValue())
        }
    }

    val beforeEachListener = object : PropTestListener {
        override suspend fun beforeTest() {
            beforeEach()
        }
    }

    "should create audio items from the same album and reflect changes in the repository" {
        checkAll(10, PropTestConfig(listeners = listOf(beforeEachListener)), arbitraryAlbumAudioItems()) { testAlbumAudioItems ->
            val expectedArtist = testAlbumAudioItems[0].artist
            val expectedAlbum = testAlbumAudioItems[0].album

            testAlbumAudioItems.forEach { audioRepository.createFromFile(it.path) }

            eventually(100.milliseconds) {
                val currentAlbumItems = audioRepository.search { it.album.name == expectedAlbum.name }
                currentAlbumItems.size shouldBe testAlbumAudioItems.size
                audioRepository.findAlbumAudioItems(expectedArtist, expectedAlbum.name) shouldContainExactlyInAnyOrder currentAlbumItems
            }
        }
    }
})
