package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.data.StandardDataEvent
import net.transgressoft.commons.data.json.JsonFileRepository
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

typealias WaveformRepository<I> = AudioWaveformRepository<ScalableAudioWaveform, I>

class AudioWaveformJsonRepository<I : ReactiveAudioItem<I>>(name: String, file: File) :
    JsonFileRepository<Int, ScalableAudioWaveform>(file, MapSerializer(Int.serializer(), ScalableAudioWaveform.serializer()), name = name),
    WaveformRepository<I> {

    override val audioItemEventSubscriber = AudioItemEventSubscriber<I>(this.toString()).apply {
        addOnNextEventAction(StandardDataEvent.Type.DELETE) { event ->
            removeByAudioItemIds(event.entitiesById.keys)
        }
    }

    override fun getOrCreateWaveformAsync(audioItem: I, width: Short, height: Short): CompletableFuture<ScalableAudioWaveform> {
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

    override fun toString() = "WaveformRepository(name=$name, waveformsCount=${size()})"
}
