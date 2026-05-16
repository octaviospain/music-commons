package net.transgressoft.commons.media.waveform

import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventSubscriberBase
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.json.JsonRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import io.mockk.unmockkAll
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class DefaultAudioWaveformRepositoryTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()
    lateinit var jsonFile: File
    lateinit var jsonFileRepository: JsonRepository<Int, AudioWaveform>
    lateinit var audioWaveformRepository: AudioWaveformRepository<AudioWaveform, net.transgressoft.commons.music.audio.AudioItem>

    beforeEach {
        jsonFile = tempfile("audioWaveformRepository-test", ".json").also { it.deleteOnExit() }
        jsonFileRepository = JsonFileRepository(jsonFile, AudioWaveformMapSerializer)
        val subscriber =
            object : LirpEventSubscriberBase<
                net.transgressoft.commons.music.audio.AudioItem,
                CrudEvent.Type,
                CrudEvent<Int, net.transgressoft.commons.music.audio.AudioItem>
            >("test-subscriber") {}
        audioWaveformRepository = DefaultAudioWaveformRepository(jsonFileRepository, subscriber)
    }

    afterEach {
        audioWaveformRepository.close()
        jsonFileRepository.close()
    }

    afterSpec {
        unmockkAll()
    }

    "DefaultAudioWaveformRepository add and findById in media module" {
        val audioFilePath = files.virtualAudioFile().next()
        val audioWaveform = ScalableAudioWaveform(1, audioFilePath)

        audioWaveformRepository.add(audioWaveform) shouldBe true
        audioWaveformRepository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }

        reactive.advance()
    }

    "DefaultAudioWaveformRepository loads from JSON file correctly" {
        val audioFilePath = files.virtualAudioFile().next()
        val audioWaveform = ScalableAudioWaveform(1, audioFilePath)

        audioWaveformRepository.add(audioWaveform) shouldBe true
        reactive.advance()

        val subscriber =
            object : LirpEventSubscriberBase<
                net.transgressoft.commons.music.audio.AudioItem,
                CrudEvent.Type,
                CrudEvent<Int, net.transgressoft.commons.music.audio.AudioItem>
            >("test-subscriber-2") {}
        val loadedRepository = DefaultAudioWaveformRepository<net.transgressoft.commons.music.audio.AudioItem>(jsonFileRepository, subscriber)
        loadedRepository.size() shouldBe 1
        loadedRepository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }
    }

    "audioWaveformRepository factory function creates valid repository in media module" {
        val subscriber =
            object : LirpEventSubscriberBase<
                net.transgressoft.commons.music.audio.AudioItem,
                CrudEvent.Type,
                CrudEvent<Int, net.transgressoft.commons.music.audio.AudioItem>
            >("factory-test") {}
        val repo = audioWaveformRepository(jsonFileRepository, subscriber)

        repo.size() shouldBe 0
        repo.isEmpty shouldBe true
    }
})