package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.data.StandardDataEvent
import net.transgressoft.commons.data.json.JsonFileRepository
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

typealias WaveformRepository = AudioWaveformRepository<ScalableAudioWaveform, AudioItem>

class AudioWaveformJsonRepository(name: String, file: File) :
    JsonFileRepository<Int, ScalableAudioWaveform>(name, file, MapSerializer(Int.serializer(), ScalableAudioWaveform.serializer())),
    WaveformRepository {

    override val audioItemEventSubscriber = AudioItemEventSubscriber<AudioItem>(this.toString()).apply {
        addOnNextEventAction(StandardDataEvent.Type.DELETE) { event ->
            removeByAudioItemIds(event.entitiesById.keys)
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

    override fun toString() = "WaveformRepository(name=$name, waveformsCount=${size()})"
}
