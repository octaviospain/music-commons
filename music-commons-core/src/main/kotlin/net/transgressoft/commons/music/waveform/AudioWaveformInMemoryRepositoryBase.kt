package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.event.QueryEntityEvent
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.query.InMemoryRepository
import java.util.*
import java.util.concurrent.CompletableFuture

abstract class AudioWaveformInMemoryRepositoryBase<W : AudioWaveform>(entitiesById: MutableMap<Int, W> = mutableMapOf()) :
    InMemoryRepository<W>(entitiesById),
    AudioWaveformRepository<W> {

    final override val audioItemEventSubscriber = AudioItemEventSubscriber<AudioItem>().apply {
        addOnNextEventAction(QueryEntityEvent.Type.DELETE) { event ->
            removeByAudioItemIds(event.entities.map { it.id }.toList())
        }
    }

    private val emptyWaveforms: MutableMap<Pair<Short, Short>, W> = mutableMapOf()

    private fun emptyWaveform(width: Short, height: Short): W {
        val dimensionPair = Pair(width, height)
        val fakeAmplitudes = FloatArray(width.toInt())
        Arrays.fill(fakeAmplitudes, 0.0f)

        return if (emptyWaveforms.contains(dimensionPair))
            emptyWaveforms[dimensionPair]!!
        else
            createWaveform(-1, fakeAmplitudes, width.toInt(), height.toInt())
    }

    override fun getOrCreateWaveformAsync(audioItem: AudioItem, width: Short, height: Short): CompletableFuture<W> {
        return findById(audioItem.id)
            .map<CompletableFuture<W>> { CompletableFuture.completedFuture(it) }
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

    private fun create(audioItem: AudioItem, width: Short, height: Short): W {
        val amplitudes = AudioWaveformExtractor().extractWaveform(audioItem.path, width.toInt(), height.toInt())
        val waveform = createWaveform(audioItem.id, amplitudes, width.toInt(), height.toInt())
        add(waveform)
        return waveform
    }

    protected abstract fun createWaveform(id: Int, amplitudes: FloatArray, width: Int, height: Int): W

    @Override
    override fun removeByAudioItemIds(audioItemIds: List<Int>) {
        audioItemIds.forEach { findById(it).ifPresent { waveform -> remove(waveform) } }
    }
}