package net.transgressoft.commons.media.waveform

import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventSubscriberBase
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.json.JsonRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.mockk.unmockkAll
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class DefaultAudioWaveformRepositoryTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var jsonFileRepository: JsonRepository<Int, AudioWaveform>
    lateinit var audioWaveformRepository: AudioWaveformRepository<AudioWaveform, net.transgressoft.commons.music.audio.AudioItem>

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

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
        jsonFileRepository.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
        unmockkAll()
    }

    "DefaultAudioWaveformRepository add and findById in media module" {
        val audioFilePath = Arb.virtualAudioFile().next()
        val audioWaveform = ScalableAudioWaveform(1, audioFilePath)

        audioWaveformRepository.add(audioWaveform) shouldBe true
        audioWaveformRepository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }

        testDispatcher.scheduler.advanceUntilIdle()
    }

    "DefaultAudioWaveformRepository loads from JSON file correctly" {
        val audioFilePath = Arb.virtualAudioFile().next()
        val audioWaveform = ScalableAudioWaveform(1, audioFilePath)

        audioWaveformRepository.add(audioWaveform) shouldBe true
        testDispatcher.scheduler.advanceUntilIdle()

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