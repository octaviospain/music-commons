package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.data.InMemoryRepositoryBase
import net.transgressoft.commons.data.StandardDataEvent
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.toIds
import java.util.concurrent.CompletableFuture

class AudioWaveformInMemoryRepository : InMemoryRepositoryBase<ScalableAudioWaveform>(), AudioWaveformRepository<ScalableAudioWaveform> {

    override val audioItemEventSubscriber = AudioItemEventSubscriber<AudioItem>(this.toString()).apply {
        addOnNextEventAction(StandardDataEvent.Type.DELETE) { event ->
            removeByAudioItemIds(event.entities.toIds())
        }
    }

    override fun getOrCreateWaveformAsync(audioItem: AudioItem, width: Short, height: Short): CompletableFuture<ScalableAudioWaveform> {
        return findById(audioItem.id)
            .map<CompletableFuture<ScalableAudioWaveform>> { CompletableFuture.completedFuture(it) }
            .orElseGet {
                CompletableFuture.supplyAsync {
                    val audioWaveform = ScalableAudioWaveform(audioItem.id, audioItem.path)
                    add(audioWaveform)
                    return@supplyAsync audioWaveform
                }
            }
    }

    override fun toString() = "WaveformRepository[${this.hashCode()}]"
}
