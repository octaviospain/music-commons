package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.fx.music.audio.FXAudioItemTestUtil.arbitraryMp3File
import net.transgressoft.commons.fx.music.audio.FXAudioItemTestUtil.asJsonKeyValue
import net.transgressoft.commons.persistence.ReactiveScope
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.property.arbitrary.next
import java.io.File
import java.util.Map.entry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class ObservableAudioItemJsonRepositoryTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var observableAudioItemRepository: ObservableAudioItemJsonRepository

    beforeSpec {
        ReactiveScope.setDefaultFlowScope(testScope)
        ReactiveScope.setDefaultIoScope(testScope)
    }

    beforeEach {
        jsonFile = tempfile("observableAudioItemRepository-test", ".json").also { it.deleteOnExit() }
        observableAudioItemRepository = ObservableAudioItemJsonRepository("ObservableAudioItemRepo", jsonFile)
    }

    afterEach {
        observableAudioItemRepository.close()
    }

    afterSpec {
        ReactiveScope.setDefaultFlowScope(CoroutineScope(Dispatchers.Default.limitedParallelism(4) + SupervisorJob()))
        ReactiveScope.setDefaultIoScope(CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob()))
    }

    "should create an observable audio item and serialize itself" {
        val fxAudioItem = observableAudioItemRepository.createFromFile(arbitraryMp3File.next().toPath())

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText().shouldEqualJson(fxAudioItem.asJsonKeyValue())

        fxAudioItem.title = "New title"

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText() should {
            it.shouldEqualJson(fxAudioItem.asJsonKeyValue())
            it.shouldContainOnlyOnce("title\": \"New title")
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

        testDispatcher.scheduler.advanceUntilIdle()
        audioItemsSetProperty.get().find { it.key == fxAudioItem.id }?.value?.title shouldBe "New title"

        observableAudioItemRepository.remove(fxAudioItem)
        audioItemsSetProperty.isEmpty() shouldBe true
        observableAudioItemRepository.emptyLibraryProperty().get() shouldBe true
    }
})