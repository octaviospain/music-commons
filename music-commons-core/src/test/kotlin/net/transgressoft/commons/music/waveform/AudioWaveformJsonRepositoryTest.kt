package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemTestUtil
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItem
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryWavFile
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.short
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class AudioWaveformJsonRepositoryTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var audioWaveformRepository: WaveformRepository<AudioItem>

    beforeSpec {
        ReactiveScope.setDefaultFlowScope(testScope)
        ReactiveScope.setDefaultIoScope(testScope)
    }

    beforeEach {
        jsonFile = tempfile("audioWaveformRepository-test", ".json").also { it.deleteOnExit() }
        audioWaveformRepository = AudioWaveformJsonRepository("Waveforms", jsonFile)
    }

    afterEach {
        audioWaveformRepository.close()
    }

    afterSpec {
        ReactiveScope.setDefaultFlowScope(CoroutineScope(Dispatchers.Default.limitedParallelism(4) + SupervisorJob()))
        ReactiveScope.setDefaultIoScope(CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob()))
    }

    "Repository serializes itself to file when audio waveform is added" {
        val audioFilePath = AudioItemTestUtil.arbitraryMp3File.next().toPath()
        val audioWaveform = ScalableAudioWaveform(1, audioFilePath)

        audioWaveformRepository.add(audioWaveform) shouldBe true
        audioWaveformRepository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText() shouldEqualJson """
                {
                    "1": {
                        "id": 1,
                        "audioFilePath": "$audioFilePath"
                    }
                }
            """

        val loadedRepository = AudioWaveformJsonRepository<AudioItem>("Waveforms", jsonFile)
        loadedRepository.size() shouldBe 1
        loadedRepository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }
        loadedRepository shouldBe audioWaveformRepository
    }

    "Repository creates a waveform asynchronously" {
        val audioItem = arbitraryAudioItem { path = arbitraryWavFile.next().toPath() }.next()
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