package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.data.StandardDataEvent
import net.transgressoft.commons.data.json.GenericJsonFileRepository
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

typealias WaveformRepository<I> = AudioWaveformRepository<AudioWaveform, I>

class AudioWaveformJsonRepository<I : ReactiveAudioItem<I>>(name: String, file: File) :
    GenericJsonFileRepository<Int, AudioWaveform>(file, MapSerializer(Int.serializer(), AudioWaveformSerializer), name = name),
    WaveformRepository<I> {

    override val audioItemEventSubscriber = AudioItemEventSubscriber<I>(this.toString()).apply {
        addOnNextEventAction(StandardDataEvent.Type.DELETE) { event ->
            removeByAudioItemIds(event.entitiesById.keys)
        }
    }

    override fun getOrCreateWaveformAsync(audioItem: I, width: Short, height: Short): CompletableFuture<AudioWaveform> {
        return findById(audioItem.id)
            .map<CompletableFuture<AudioWaveform>> { CompletableFuture.completedFuture(it) }
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
