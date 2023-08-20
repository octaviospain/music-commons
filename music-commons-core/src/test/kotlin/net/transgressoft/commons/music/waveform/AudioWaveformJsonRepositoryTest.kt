package net.transgressoft.commons.music.waveform

import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryMp3File
import java.io.File
import kotlin.time.Duration.Companion.seconds

private lateinit var jsonFile: File
private lateinit var repository: AudioWaveformJsonRepository

internal class AudioWaveformJsonRepositoryTest : StringSpec({

    beforeEach {
        jsonFile = tempfile("json-repository-test", ".json").also { it.deleteOnExit() }
        repository = AudioWaveformJsonRepository(jsonFile)
    }

    "Repository serializes itself to file when audio waveform is added" {
        val audioFilePath = arbitraryMp3File.next().toPath()
        val audioWaveform = ScalableAudioWaveform(1, audioFilePath)

        repository.add(audioWaveform) shouldBe true
        repository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }

        eventually(2.seconds) {
            jsonFile.readText() shouldBe """
                {
                    "entitiesById": {
                        "1": {
                            "id": 1,
                            "audioFilePath": "$audioFilePath"
                        }
                    }
                }
            """.trimIndent()

            val loadedRepository = AudioWaveformJsonRepository(jsonFile)
            loadedRepository.size() shouldBe 1
            loadedRepository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }
            loadedRepository shouldBe repository
        }
    }
})
