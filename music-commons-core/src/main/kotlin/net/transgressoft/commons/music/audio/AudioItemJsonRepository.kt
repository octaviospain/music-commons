package net.transgressoft.commons.music.audio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.io.File

@Serializable
@SerialName("AudioItemRepository")
class AudioItemJsonRepository internal constructor(
    @Transient val _file: File? = null
) : AudioItemJsonRepositoryBase<AudioItem>(_file) {

    @Transient
    override var repositorySerializersModule = audioItemRepositoryBaseSerializersModule + audioItemRepositorySerializersModule

    constructor() : this(null)

    companion object {
        private val json = Json {
            serializersModule = audioItemRepositoryBaseSerializersModule + audioItemRepositorySerializersModule
            allowStructuredMapKeys = true
        }

        fun loadFromFile(file: File): AudioItemJsonRepository {
            require(file.exists().and(file.canRead().and(file.canWrite()))) {
                "Provided jsonFile does not exist or is not writable"
            }
            return json.decodeFromString(serializer(AudioItemBase.serializer()), file.readText()) as AudioItemJsonRepository
        }

        fun initialize(file: File) = AudioItemJsonRepository(file)
    }
}

val audioItemRepositorySerializersModule = SerializersModule {
    polymorphic(AudioItemJsonRepositoryBase::class) {
        subclass(AudioItemJsonRepository::class)
    }
    polymorphic(AudioItemBase::class) {
        subclass(ImmutableAudioItem::class)
    }
}