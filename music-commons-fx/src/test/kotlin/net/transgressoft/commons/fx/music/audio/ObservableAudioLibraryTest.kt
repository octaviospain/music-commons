package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.music.audio.shouldEqual
import net.transgressoft.commons.persistence.json.JsonFileRepository
import net.transgressoft.commons.persistence.json.JsonRepository
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class ObservableAudioLibraryTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var jsonFileRepository: JsonRepository<Int, ObservableAudioItem>
    lateinit var repository: ObservableAudioLibrary

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
        FxToolkit.registerPrimaryStage()
    }

    beforeEach {
        jsonFile = tempfile("observableAudioItemRepository-test", ".json").also { it.deleteOnExit() }
        jsonFileRepository = JsonFileRepository(jsonFile, ObservableAudioItemMapSerializer)
        repository = ObservableAudioLibrary(jsonFileRepository)
    }

    afterEach {
        jsonFileRepository.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "Repository should create an observable audio item and serialize itself" {
        val fxAudioItem = repository.createFromFile(Arb.virtualAudioFile().next())

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile shouldEqual fxAudioItem.asJsonKeyValue()

        fxAudioItem.title = "New title"

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText() should {
            it.shouldEqualJson(fxAudioItem.asJsonKeyValue())
            it.shouldContainOnlyOnce("title\": \"New title")
        }
    }

    "Repository should expose changes on the ReadOnlySetProperty" {
        val audioItemsProperty = repository.audioItemsProperty
        repository.emptyLibraryProperty.get() shouldBe true
        repository.audioItemsProperty.isEmpty() shouldBe true

        val fxAudioItem = repository.createFromFile(Arb.virtualAudioFile().next())
        audioItemsProperty.contains(fxAudioItem) shouldBe true
        repository.emptyLibraryProperty.get() shouldBe false

        fxAudioItem.title = "New title"

        testDispatcher.scheduler.advanceUntilIdle()
        var foundItem = audioItemsProperty.get().find { it.id == fxAudioItem.id }
        foundItem.shouldNotBeNull()
        foundItem.title shouldBe "New title"

        repository.remove(fxAudioItem)
        audioItemsProperty.isEmpty() shouldBe true
        repository.emptyLibraryProperty.get() shouldBe true
    }

    "Repository items are reflected on the ReadOnlySetProperty after loading from Json file" {
        val fxAudioItem = repository.createFromFile(Arb.virtualAudioFile().next())

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile shouldEqual fxAudioItem.asJsonKeyValue()

        repository = ObservableAudioLibrary(jsonFileRepository)

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