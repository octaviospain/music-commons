package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.music.audio.audioItem
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.short
import io.mockk.unmockkAll
import java.io.File
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@ExperimentalCoroutinesApi
internal class AudioWaveformJsonRepositoryTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var audioWaveformRepository: WaveformRepository<AudioItem>

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        jsonFile = tempfile("audioWaveformRepository-test", ".json").also { it.deleteOnExit() }
        audioWaveformRepository = AudioWaveformJsonRepository("Waveforms", jsonFile)
    }

    afterEach {
        audioWaveformRepository.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
        unmockkAll()
    }

    "Repository serializes itself to file when audio waveform is added" {
        val audioFilePath = Arb.virtualAudioFile().next()
        val audioWaveform = ScalableAudioWaveform(1, audioFilePath)

        audioWaveformRepository.add(audioWaveform) shouldBe true
        audioWaveformRepository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText() shouldEqualJson
            buildJsonObject {
                put(
                    "${audioWaveform.id}",
                    buildJsonObject {
                        put("id", audioWaveform.id)
                        put("audioFilePath", audioWaveform.audioFilePath.absolutePathString())
                    }
                )
            }.toString()

        val loadedRepository = AudioWaveformJsonRepository<AudioItem>("Waveforms", jsonFile)
        loadedRepository.size() shouldBe 1
        loadedRepository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }
        loadedRepository shouldBe audioWaveformRepository
    }

    "Repository creates a waveform asynchronously" {
        val audioItem =
            Arb.audioItem {
                path = Arb.virtualAudioFile(WAV).next()
            }.next()
        var audioWaveform = audioWaveformRepository.getOrCreateWaveformAsync(audioItem, Arb.short(1, 50).next(), Arb.short(1, 50).next(), testDispatcher).get()

        testDispatcher.scheduler.advanceUntilIdle()

        audioWaveform.id shouldBe audioItem.id
        audioWaveformRepository.findById(audioItem.id) shouldBePresent { it shouldBe audioWaveform }
        audioWaveformRepository.contains { it == audioWaveform }

        val waveform2 = audioWaveformRepository.getOrCreateWaveformAsync(audioItem, Arb.short(1, 100).next(), Arb.short(1, 100).next(), testDispatcher).get()

        testDispatcher.scheduler.advanceUntilIdle()

        waveform2 shouldBe audioWaveform
        audioWaveformRepository.removeByAudioItemIds(setOf(audioItem.id))
        audioWaveformRepository.isEmpty shouldBe true
    }
})