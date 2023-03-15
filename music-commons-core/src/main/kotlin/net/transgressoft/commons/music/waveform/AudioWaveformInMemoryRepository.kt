package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.event.QueryEntityEvent
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.query.InMemoryRepository
import java.util.concurrent.CompletableFuture

class AudioWaveformInMemoryRepository(entitiesById: MutableMap<Int, ScalableAudioWaveform> = mutableMapOf()) :
    InMemoryRepository<ScalableAudioWaveform>(entitiesById),
    AudioWaveformRepository<ScalableAudioWaveform> {

    override val audioItemEventSubscriber = AudioItemEventSubscriber<AudioItem>().apply {
        addOnNextEventAction(QueryEntityEvent.Type.DELETE) { event ->
            removeByAudioItemIds(event.entities.map { it.id }.toList())
        }
    }

    override fun getOrCreateWaveformAsync(audioItem: AudioItem, width: Short, height: Short): CompletableFuture<ScalableAudioWaveform> {
        return findById(audioItem.id)
            .map<CompletableFuture<ScalableAudioWaveform>> { CompletableFuture.completedFuture(it) }
            .orElseGet {
                CompletableFuture.supplyAsync {
                    return@supplyAsync ScalableAudioWaveform(audioItem.id, audioItem.path)
                }
            }
    }

    @Override
    override fun removeByAudioItemIds(audioItemIds: List<Int>) {
        audioItemIds.forEach { findById(it).ifPresent { waveform -> remove(waveform) } }
    }
}