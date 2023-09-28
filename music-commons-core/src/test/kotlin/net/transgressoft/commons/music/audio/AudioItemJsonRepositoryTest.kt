package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioAttributes
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryM4aFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryMp3File
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.*
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import java.io.File
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKotest::class)
internal class AudioItemJsonRepositoryTest : StringSpec({

    lateinit var jsonFile: File
    lateinit var audioRepository: AudioItemRepository<MutableAudioItem>

    fun beforeEach() {
        jsonFile = tempfile("audioItemRepository-test", ".json").also { it.deleteOnExit() }
        audioRepository = AudioItemJsonRepository("AudioRepo", jsonFile)
    }

    beforeEach { beforeEach() }

    "should create an audio item and allow to query it" {
        audioRepository.createFromFile(arbitraryMp3File.next().toPath()) should {
            it.id shouldNotBe UNASSIGNED_ID
            it.album.audioItems.shouldContainExactly(it)
            audioRepository.add(it) shouldBe false
            audioRepository.addOrReplace(it) shouldBe false
            audioRepository.contains { audioItem -> audioItem == it } shouldBe true
            audioRepository.search { audioItem -> audioItem == it }.shouldContainExactly(it)
            audioRepository.findByUniqueId(it.uniqueId) shouldBePresent { found -> found shouldBe it }
            audioRepository.containsAudioItemWithArtist(it.artist.name) shouldBe true
            audioRepository.containsAudioItemWithArtist(it.album.albumArtist.name) shouldBe true
            audioRepository.getArtistCatalog(it.artist) shouldBePresent { artistCatalog ->
                artistCatalog.artist shouldBe it.artist
                artistCatalog.size shouldBe 1
                artistCatalog.containsAudioItem(it)
            }
        }
    }

    val beforeEachListener = object : PropTestListener {
        override suspend fun beforeTest() {
            beforeEach()
        }
    }

    "should create audio items and reflect changes in the repository" {
        checkAll(10, PropTestConfig(listeners = listOf(beforeEachListener)),
            Arb.list(arbitraryAudioAttributes(
            artist = ImmutableArtist.of("Pixies"),
            album = ImmutableAlbum("Doolittle", ImmutableArtist.of("Pixies")),
            coverImageBytes = null
        ), 1 .. 10)) { testAudioAttributes ->
            val doolittleAlbumFiles = testAudioAttributes.map { arbitraryMp3File(it).next() }

            doolittleAlbumFiles.forEachIndexed { index, file ->
                val audioItem = audioRepository.createFromFile(file.toPath())
                audioItem.artist.name shouldBe "Pixies"
                audioItem.album.name shouldBe "Doolittle"

                eventually(1.seconds) {
                    val currentAlbumItems = audioRepository.search { it.album.name == "Doolittle" }
                    currentAlbumItems.size shouldBe index + 1
                    audioItem.album.audioItems shouldContainExactlyInAnyOrder currentAlbumItems
                }
            }
        }
    }

    "should reflect changes when audio item is updated" {
        val audioItem = audioRepository.createFromFile(arbitraryM4aFile.next().toPath())

        val updatedAudioItem = audioItem.update { title = "New title" }
        updatedAudioItem shouldBeSameInstanceAs audioItem
        updatedAudioItem.title shouldBe "New title"

        audioRepository.contains { it.title == "New title" } shouldBe true
        audioRepository.search { it.title == "New title" }.shouldContainExactly(audioItem)
    }
})
