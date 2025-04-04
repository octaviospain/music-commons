package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.fx.music.audio.FXAudioItemTestUtil.arbitraryAudioItem
import net.transgressoft.commons.fx.music.audio.FXAudioItemTestUtil.arbitraryMp3File
import net.transgressoft.commons.fx.music.audio.FXAudioItemTestUtil.asJsonKeyValue
import net.transgressoft.commons.fx.music.audio.FXAudioItemTestUtil.asJsonKeyValues
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.property.arbitrary.next
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class ObservableAudioItemJsonRepositoryTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var repository: ObservableAudioItemJsonRepository

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        jsonFile = tempfile("observableAudioItemRepository-test", ".json").also { it.deleteOnExit() }
        repository = ObservableAudioItemJsonRepository("ObservableAudioItemRepo", jsonFile)
    }

    afterEach {
        repository.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "Repository should create an observable audio item and serialize itself" {
        val fxAudioItem = repository.createFromFile(arbitraryMp3File.next().toPath())

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText().shouldEqualJson(fxAudioItem.asJsonKeyValue())

        fxAudioItem.title = "New title"

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText() should {
            it.shouldEqualJson(fxAudioItem.asJsonKeyValue())
            it.shouldContainOnlyOnce("title\": \"New title")
        }
    }

    "Repository should expose changes on the ReadOnlySetProperty" {
        val audioItemsProperty = repository.audioItemsProperty
        repository.emptyLibraryProperty().get() shouldBe true
        repository.audioItemsProperty.isEmpty() shouldBe true

        val fxAudioItem = repository.createFromFile(arbitraryMp3File.next().toPath())
        audioItemsProperty.contains(fxAudioItem) shouldBe true
        repository.emptyLibraryProperty().get() shouldBe false

        fxAudioItem.title = "New title"

        testDispatcher.scheduler.advanceUntilIdle()
        var foundItem = audioItemsProperty.get().find { it.id == fxAudioItem.id }
        foundItem.shouldNotBeNull()
        foundItem.title shouldBe "New title"

        repository.remove(fxAudioItem)
        audioItemsProperty.isEmpty() shouldBe true
        repository.emptyLibraryProperty().get() shouldBe true
    }

    "Repository items are reflected on the ReadOnlySetProperty after loading from Json file" {
        val fxAudioItem = arbitraryAudioItem.next()
        jsonFile.writeText(listOf(fxAudioItem).asJsonKeyValues())

        repository = ObservableAudioItemJsonRepository("ObservableAudioItemRepo", jsonFile)

        testDispatcher.scheduler.advanceUntilIdle()

        repository.size() shouldBe 1
        repository.findById(fxAudioItem.id) shouldBePresent { it shouldBe fxAudioItem }
        repository.audioItemsProperty.shouldContainOnly(fxAudioItem)

        repository.remove(fxAudioItem)

        testDispatcher.scheduler.advanceUntilIdle()

        repository.size() shouldBe 0
        repository.audioItemsProperty.isEmpty() shouldBe true
    }
})