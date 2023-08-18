package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.event.QueryEntityEvent
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.query.InMemoryRepositoryBase
import java.util.concurrent.CompletableFuture

class AudioWaveformInMemoryRepository : InMemoryRepositoryBase<ScalableAudioWaveform>(), AudioWaveformRepository<ScalableAudioWaveform> {

    override val audioItemEventSubscriber = AudioItemEventSubscriber<AudioItem>(this.toString()).apply {
        addOnNextEventAction(QueryEntityEvent.Type.DELETE) { event ->
            removeByAudioItemIds(event.entities.map { it.id }.toList())
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
