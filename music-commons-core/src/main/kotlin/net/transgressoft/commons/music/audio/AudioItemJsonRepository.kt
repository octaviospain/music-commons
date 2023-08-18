package net.transgressoft.commons.music.audio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.transgressoft.commons.event.QueryEntitySubscriberBase
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.query.JsonFileRepository
import java.io.File

@Serializable
@SerialName("AudioItemRepository")
class AudioItemJsonRepository internal constructor(
    @Transient val _file: File? = null
) : AudioItemJsonRepositoryBase<AudioItemBase>(_file) {

    @Transient
    override var queryEntitySerializer = AudioItemBase.serializer()

    @Transient
    override var polymorphicRepositorySerializer = audioItemRepositorySerializersModule

    constructor() : this(null)

    companion object {
        private val json = Json {
            serializersModule = audioItemRepositorySerializersModule
            allowStructuredMapKeys = true
        }

        fun loadFromFile(file: File): JsonFileRepository<AudioItemBase> {
            require(file.exists().and(file.canRead().and(file.canWrite()))) {
                "Provided jsonFile does not exist or is not writable"
            }
            return json.decodeFromString(JsonFileRepository.serializer(AudioItemBase.serializer()), file.readText())
                .apply {
                    queryEntitySerializer = AudioItemBase.serializer()
                    polymorphicRepositorySerializer = audioItemRepositorySerializersModule
                }
        }

        fun initialize(file: File) = AudioItemJsonRepository(file)
    }
}

val audioItemRepositorySerializersModule = SerializersModule {
    polymorphic(JsonFileRepository::class) {
        subclass(AudioItemJsonRepository.serializer())
    }
    polymorphic(QueryEntitySubscriberBase::class) {
        subclass(AudioItemEventSubscriber.serializer(AudioItemBase.serializer()))
    }
    include(audioItemSerializerModule)
}