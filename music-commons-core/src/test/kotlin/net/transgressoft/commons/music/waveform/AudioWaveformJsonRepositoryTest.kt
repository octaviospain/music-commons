package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemTestUtil
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItem
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryWavFile
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.short
import java.io.File
import kotlin.time.Duration.Companion.seconds

internal class AudioWaveformJsonRepositoryTest : StringSpec({

    lateinit var jsonFile: File
    lateinit var audioWaveformRepository: WaveformRepository<AudioItem>

    beforeEach {
        jsonFile = tempfile("audioWaveformRepository-test", ".json").also { it.deleteOnExit() }
        audioWaveformRepository = AudioWaveformJsonRepository("Waveforms", jsonFile)
    }

    "Repository serializes itself to file when audio waveform is added" {
        val audioFilePath = AudioItemTestUtil.arbitraryMp3File.next().toPath()
        val audioWaveform = ScalableAudioWaveform(1, audioFilePath)

        audioWaveformRepository.add(audioWaveform) shouldBe true
        audioWaveformRepository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }

        eventually(2.seconds) {
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
    }

    "Repository creates a waveform asynchronously" {
        val audioItem = arbitraryAudioItem { path = arbitraryWavFile.next().toPath() }.next()
        val audioWaveform = audioWaveformRepository.getOrCreateWaveformAsync(audioItem, Arb.short(1, 100).next(), Arb.short(1, 100).next()).join()

        audioWaveform.id shouldBe audioItem.id
        audioWaveformRepository.findById(audioItem.id) shouldBePresent { it shouldBe audioWaveform }
        audioWaveformRepository.contains { it == audioWaveform }
        audioWaveformRepository.getOrCreateWaveformAsync(audioItem, Arb.short(1, 100).next(), Arb.short(1, 100).next()).join() shouldBe audioWaveform

        audioWaveformRepository.removeByAudioItemIds(setOf(audioItem.id))
        audioWaveformRepository.isEmpty shouldBe true
    }
})