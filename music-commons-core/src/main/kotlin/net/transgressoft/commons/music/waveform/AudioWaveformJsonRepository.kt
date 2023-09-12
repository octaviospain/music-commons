package net.transgressoft.commons.music.waveform

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.transgressoft.commons.data.JsonFileRepository
import net.transgressoft.commons.data.StandardDataEvent
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.toIds
import java.io.File
import java.util.concurrent.CompletableFuture

class AudioWaveformJsonRepository(file: File) :
    JsonFileRepository<ScalableAudioWaveform, Int>(file, Int.serializer(), ScalableAudioWaveform.serializer()),
    AudioWaveformRepository<ScalableAudioWaveform> {

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

    companion object {
        val audioWaveformRepositorySerializersModule = SerializersModule {
            polymorphic(AudioWaveform::class) {
                subclass(ScalableAudioWaveform::class)
            }
        }
    }
}