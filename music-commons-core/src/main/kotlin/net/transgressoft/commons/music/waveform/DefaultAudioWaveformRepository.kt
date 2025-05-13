package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.commons.persistence.Repository
import net.transgressoft.commons.persistence.VolatileRepository
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.future

class DefaultAudioWaveformRepository<I: ReactiveAudioItem<I>>(
    private val repository: Repository<Int, AudioWaveform> = VolatileRepository("AudioWaveformRepository")
): AudioWaveformRepository<AudioWaveform, I>, Repository<Int, AudioWaveform> by repository {

    override val audioItemEventSubscriber =
        AudioItemEventSubscriber<I>(this.toString()).apply {
            addOnNextEventAction(DELETE) { event ->
                removeByAudioItemIds(event.entities.keys)
            }
        }

    override fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short,
        dispatcher: CoroutineDispatcher
    ): CompletableFuture<AudioWaveform> =
        findById(audioItem.id)
            .map { CompletableFuture.completedFuture(it) }
            .orElseGet {
                CoroutineScope(dispatcher).future {
                    async {
                        val audioWaveform = ScalableAudioWaveform(audioItem.id, audioItem.path)
                        add(audioWaveform)
                        audioWaveform
                    }.await()
                }
            }

    override fun hashCode() = repository.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DefaultAudioWaveformRepository<*>) return false
        if (repository != other.repository) return false
        return true
    }

    override fun toString() = "WaveformRepository(waveformsCount=${size()})"
}