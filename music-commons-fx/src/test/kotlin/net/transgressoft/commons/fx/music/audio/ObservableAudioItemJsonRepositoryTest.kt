package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.fx.music.audio.FXAudioItemTestUtil.arbitraryMp3File
import net.transgressoft.commons.fx.music.audio.FXAudioItemTestUtil.asJsonKeyValue
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.property.arbitrary.next
import java.io.File
import java.util.Map.*
import kotlin.time.Duration.Companion.milliseconds

internal class ObservableAudioItemJsonRepositoryTest : StringSpec({

    lateinit var jsonFile: File
    lateinit var observableAudioItemRepository: ObservableAudioItemJsonRepository

    beforeEach {
        jsonFile = tempfile("observableAudioItemRepository-test", ".json").also { it.deleteOnExit() }
        observableAudioItemRepository = ObservableAudioItemJsonRepository("ObservableAudioItemRepo", jsonFile)
    }

    "should create an observable audio item and serialize itself" {
        val fxAudioItem = observableAudioItemRepository.createFromFile(arbitraryMp3File.next().toPath())

        eventually(100.milliseconds) { jsonFile.readText().shouldEqualJson(fxAudioItem.asJsonKeyValue()) }

        fxAudioItem.title = "New title"

        eventually(100.milliseconds) {
            jsonFile.readText() should {
                it.shouldEqualJson(fxAudioItem.asJsonKeyValue())

                it.shouldContainOnlyOnce("title\": \"New title")
            }
        }
    }

    "should expose changes on the ReadOnlySetProperty" {
        val audioItemsSetProperty = observableAudioItemRepository.setProperty
        observableAudioItemRepository.emptyLibraryProperty().get() shouldBe true
        observableAudioItemRepository.setProperty.isEmpty() shouldBe true

        val fxAudioItem = observableAudioItemRepository.createFromFile(arbitraryMp3File.next().toPath())
        audioItemsSetProperty.contains(entry(fxAudioItem.id, fxAudioItem)) shouldBe true
        observableAudioItemRepository.emptyLibraryProperty().get() shouldBe false

        fxAudioItem.title = "New title"
        eventually(100.milliseconds) {
            audioItemsSetProperty.get().find { it.key == fxAudioItem.id }?.value?.title shouldBe "New title"
        }

        observableAudioItemRepository.remove(fxAudioItem)
        audioItemsSetProperty.isEmpty() shouldBe true
        observableAudioItemRepository.emptyLibraryProperty().get() shouldBe true
    }
})
