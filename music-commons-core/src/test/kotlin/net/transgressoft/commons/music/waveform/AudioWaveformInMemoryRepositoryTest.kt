package net.transgressoft.commons.music.waveform

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.short
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItem
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryWavFile

internal class AudioWaveformInMemoryRepositoryTest : StringSpec({

    "Repository creates a waveform asynchronously" {
        val repository = AudioWaveformInMemoryRepository()
        val audioItem = arbitraryAudioItem(path = arbitraryWavFile.next().toPath()).next()
        val audioWaveform = repository.getOrCreateWaveformAsync(audioItem, Arb.short(1, 100).next(), Arb.short(1, 100).next()).join()

        audioWaveform.id shouldBe audioItem.id
        repository.findById(audioItem.id) shouldBePresent { it shouldBe audioWaveform }
        repository.shouldContainExactly(audioWaveform)
        repository.getOrCreateWaveformAsync(audioItem, Arb.short(1, 100).next(), Arb.short(1, 100).next()).join() shouldBe audioWaveform

        repository.removeByAudioItemIds(listOf(audioItem.id))
        repository.isEmpty shouldBe true
    }
})
