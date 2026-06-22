package net.transgressoft.commons.media.waveform

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventSubscriberBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class DefaultAudioWaveformRepositoryTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()
    lateinit var backingRepository: Repository<Int, AudioWaveform>
    lateinit var audioWaveformRepository: AudioWaveformRepository<AudioWaveform, AudioItem>

    beforeEach {
        backingRepository = VolatileRepository("audioWaveformRepository-test")
        val subscriber =
            object : LirpEventSubscriberBase<
                AudioItem,
                CrudEvent.Type,
                CrudEvent<Int, AudioItem>
            >("test-subscriber") {}
        audioWaveformRepository = DefaultAudioWaveformRepository(backingRepository, subscriber)
    }

    afterEach {
        audioWaveformRepository.close()
        backingRepository.close()
    }

    "DefaultAudioWaveformRepository add and findById in media module" {
        val audioFilePath = files.virtualAudioFile().next()
        val audioWaveform = ScalableAudioWaveform(1, audioFilePath)

        audioWaveformRepository.add(audioWaveform) shouldBe true
        audioWaveformRepository.findById(audioWaveform.id) shouldBePresent { found -> found shouldBe audioWaveform }

        reactive.advance()
    }

    "audioWaveformRepository factory function creates valid repository in media module" {
        val subscriber =
            object : LirpEventSubscriberBase<
                AudioItem,
                CrudEvent.Type,
                CrudEvent<Int, AudioItem>
            >("factory-test") {}
        val repo = audioWaveformRepository(backingRepository, subscriber)

        repo.size() shouldBe 0
        repo.isEmpty shouldBe true
    }
})