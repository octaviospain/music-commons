package net.transgressoft.commons.music.audio

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.transgressoft.commons.query.JsonFileRepository
import java.io.File

val audioItemRepositorySerializersModule = SerializersModule {
    polymorphic(JsonFileRepository::class) {
        subclass(AudioItemJsonFileRepository.serializer())
    }
    polymorphic(AudioItemBase::class) {
        subclass(ImmutableAudioItem.serializer())
    }
}

@Serializable
open class AudioItemJsonFileRepository protected constructor() :
    AudioItemJsonFileRepositoryBase<AudioItemBase>(JAudioTaggerMetadataReader(), immutableAudioItemUpdateFunction) {

    @Transient
    override lateinit var jsonFile: File

    @Transient
    override var queryEntitySerializer = AudioItemBase.serializer()

    @Transient
    override var polymorphicRepositorySerializer = audioItemRepositorySerializersModule

    protected constructor(file: File) : this() {
        jsonFile = file
    }

    companion object {
        private val json = Json { serializersModule = audioItemRepositorySerializersModule }

        fun loadFromFile(file: File): JsonFileRepository<AudioItemBase> {
            return json.decodeFromString(JsonFileRepository.serializer(AudioItemBase.serializer()), file.readText())
                .apply {
                    jsonFile = file
                    queryEntitySerializer = AudioItemBase.serializer()
                    polymorphicRepositorySerializer = audioItemRepositorySerializersModule
                }
        }

        fun initialize(file: File) = AudioItemJsonFileRepository(file)
    }
}