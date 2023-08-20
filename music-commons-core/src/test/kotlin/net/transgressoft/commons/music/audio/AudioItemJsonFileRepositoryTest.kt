package net.transgressoft.commons.music.audio

import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryMp3File
import java.io.File
import kotlin.time.Duration.Companion.seconds

private lateinit var jsonFile: File
private lateinit var repository: AudioItemJsonRepository

internal class AudioItemJsonFileRepositoryTest : StringSpec({

    beforeEach {
        jsonFile = tempfile("json-repository-test", ".json").also { it.deleteOnExit() }
        repository = AudioItemJsonRepository(jsonFile)
    }

    "Repository serializes itself to file when audio item is added" {
        val audioItem = ImmutableAudioItem.createFromFile(arbitraryMp3File.next().toPath()).let {
            it.id shouldBe 0
            repository.add(it) shouldBe true
            repository.findByUniqueId(it.uniqueId) shouldBePresent { found -> found shouldBe it }
        }

        eventually(2.seconds) {
            val loadedRepository = AudioItemJsonRepository(jsonFile)
            loadedRepository.size() shouldBe 1
            loadedRepository.findById(audioItem.id) shouldBePresent {
                it shouldBe audioItem
                it.id shouldNotBe 0
            }
            loadedRepository shouldBe repository
        }
    }

    "Repository serializes itself to file when audio item is replaced" {
        val audioItem = ImmutableAudioItem.createFromFile(arbitraryMp3File.next().toPath()).let {
            repository.add(it)
            repository.findByUniqueId(it.uniqueId).get()
        }

        val audioItemModified = audioItem.update { title = "New Title" }
        repository.addOrReplace(audioItemModified) shouldBe true

        eventually(2.seconds) {
            val loadedRepository = AudioItemJsonRepository(jsonFile)
            loadedRepository.size() shouldBe 1
            loadedRepository.findById(audioItem.id) shouldBePresent {
                it shouldBe audioItemModified
                it.title shouldBe "New Title"
            }
            loadedRepository shouldBe repository
        }
    }

    "Child class from AudioItemRepositoryBase works as expected" {
        val extendedRepository = ExtendedAudioItemRepository.initialize(jsonFile)

        val extendedAudioItem = ExtendedAudioItem.createFromFile(arbitraryMp3File.next().toPath()).let {
            extendedRepository.add(it) shouldBe true
            extendedRepository.findByUniqueId(it.uniqueId) shouldBePresent { found -> found shouldBe it }
        }

        eventually(2.seconds) {
            val loadedRepository = ExtendedAudioItemRepository.loadFromFile(jsonFile)
            loadedRepository.size() shouldBe 1
            loadedRepository.findById(extendedAudioItem.id) shouldBePresent {
                it shouldBe extendedAudioItem
                it.id shouldNotBe 0
            }
            loadedRepository shouldBe extendedRepository
        }
    }
})