package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.commons.persistence.json.JsonFileRepositoryBase
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.future
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

typealias WaveformRepository<I> = AudioWaveformRepository<AudioWaveform, I>

class AudioWaveformJsonRepository<I: ReactiveAudioItem<I>>(
    name: String,
    file: File
): JsonFileRepositoryBase<Int, AudioWaveform>(name, file, MapSerializer(Int.serializer(), AudioWaveformSerializer)),
    WaveformRepository<I> {

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

    override fun toString() = "WaveformRepository(waveformsCount=${size()})"
}