package net.transgressoft.commons.music.waveform

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.transgressoft.commons.event.QueryEntityEvent
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.query.JsonFileRepository
import java.io.File
import java.util.concurrent.CompletableFuture

@Serializable
@SerialName("AudioWaveformRepository")
class AudioWaveformJsonRepository(@Transient val file: File? = null) :
    JsonFileRepository<ScalableAudioWaveform>(file),
    AudioWaveformRepository<ScalableAudioWaveform> {

    @Transient
    override var repositorySerializersModule = audioWaveformRepositorySerializersModule

    @Transient
    override var repositorySerializer: KSerializer<*> = serializer()

    @Transient
    override val audioItemEventSubscriber = AudioItemEventSubscriber<AudioItem>(this.toString()).apply {
        addOnNextEventAction(QueryEntityEvent.Type.DELETE) { event ->
            removeByAudioItemIds(event.entities.map { it.id }.toList())
        }
    }

    init {
        require(file?.exists()?.and(file.canWrite().and(file.extension == "json")) ?: true) {
            "Provided jsonFile does not exist, is not writable or is not a json file"
        }
        if (jsonFile?.readText()?.isNotEmpty() == true) {
            json.decodeFromString(serializer(), file!!.readText()).let {
                entitiesById.putAll(it.entitiesById)
            }
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