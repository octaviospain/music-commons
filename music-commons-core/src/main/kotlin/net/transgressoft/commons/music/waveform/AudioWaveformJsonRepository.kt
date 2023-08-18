package net.transgressoft.commons.music.waveform

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
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
class AudioWaveformJsonRepository internal constructor(@Transient val file: File? = null) :
    JsonFileRepository<ScalableAudioWaveform>(file),
    AudioWaveformRepository<ScalableAudioWaveform> {

    @Transient
    override var polymorphicRepositorySerializer = audioWaveformRepositorySerializersModule

    @Transient
    override var queryEntitySerializer = ScalableAudioWaveform.serializer()

    @Transient override val audioItemEventSubscriber = AudioItemEventSubscriber<AudioItem>(this.toString()).apply {
        addOnNextEventAction(QueryEntityEvent.Type.DELETE) { event ->
            removeByAudioItemIds(event.entities.map { it.id }.toList())
        }
    }

    companion object {
        private val json = Json { serializersModule = audioWaveformRepositorySerializersModule }

        fun loadFromFile(file: File): JsonFileRepository<ScalableAudioWaveform> {
            require(file.exists().and(file.canRead().and(file.canWrite()))) {
                "Provided jsonFile does not exist or is not writable"
            }
            return json.decodeFromString(JsonFileRepository.serializer(ScalableAudioWaveform.serializer()), file.readText())
                .apply {
                    queryEntitySerializer = ScalableAudioWaveform.serializer()
                    polymorphicRepositorySerializer = audioWaveformRepositorySerializersModule
                }
        }

        fun initialize(file: File) = AudioWaveformJsonRepository(file)
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
}

val audioWaveformRepositorySerializersModule = SerializersModule {
    polymorphic(JsonFileRepository::class) {
        subclass(AudioWaveformJsonRepository.serializer())
    }
}