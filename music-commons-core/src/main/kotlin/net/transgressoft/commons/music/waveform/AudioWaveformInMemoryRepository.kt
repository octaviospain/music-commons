package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.event.QueryEntityEvent
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.query.InMemoryRepository
import java.util.*
import java.util.concurrent.CompletableFuture

open class AudioWaveformInMemoryRepository(entitiesById: MutableMap<Int, AudioWaveform> = mutableMapOf()) :
    InMemoryRepository<AudioWaveform>(entitiesById),
    AudioWaveformRepository<AudioWaveform> {

    final override val audioItemEventSubscriber = AudioItemEventSubscriber().apply {
        addOnNextEventAction(QueryEntityEvent.Type.DELETE) { event ->
            removeByAudioItemIds(event.entities.map { it.id }.toList())
        }
    }

    private val emptyWaveforms: MutableMap<Pair<Short, Short>, AudioWaveform> = mutableMapOf()

    private fun emptyWaveform(width: Short, height: Short): AudioWaveform {
        val dimensionPair = Pair(width, height)
        val fakeAmplitudes = FloatArray(width.toInt())
        Arrays.fill(fakeAmplitudes, 0.0f)

        return if (emptyWaveforms.contains(dimensionPair))
            emptyWaveforms[dimensionPair]!!
        else
            ImmutableAudioWaveform(-1, fakeAmplitudes, width.toInt(), height.toInt())
    }

    override fun getOrCreateWaveformAsync(audioItem: AudioItem, width: Short, height: Short): CompletableFuture<AudioWaveform> {
        return findById(audioItem.id)
            .map<CompletableFuture<AudioWaveform>> { CompletableFuture.completedFuture(it) }
            .orElseGet {
                CompletableFuture.supplyAsync {
                    try {
                        return@supplyAsync create(audioItem, width, height)
                    } catch (exception: AudioWaveformProcessingException) {
                        return@supplyAsync emptyWaveform(width, height)
                    }
                }
            }
    }

    private fun create(audioItem: AudioItem, width: Short, height: Short): AudioWaveform {
        Objects.requireNonNull(audioItem)
        val amplitudes = AudioWaveformExtractor().extractWaveform(audioItem.path, width.toInt(), height.toInt())
        val waveform = ImmutableAudioWaveform(audioItem.id, amplitudes, width.toInt(), height.toInt())
        add(waveform)
        return waveform
    }

    @Override
    override fun removeByAudioItemIds(audioItemIds: List<Int>) {
        audioItemIds.forEach { findById(it).ifPresent { waveform -> remove(waveform) } }
    }
}