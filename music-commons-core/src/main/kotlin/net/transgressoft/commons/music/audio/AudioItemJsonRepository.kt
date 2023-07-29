package net.transgressoft.commons.music.audio

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.transgressoft.commons.query.JsonFileRepository
import java.io.File

@Serializable
class AudioItemJsonRepository() : AudioItemJsonRepositoryBase<AudioItemBase>() {

    @Transient
    override var queryEntitySerializer = AudioItemBase.serializer()

    @Transient
    override var polymorphicRepositorySerializer = audioItemRepositorySerializersModule

    constructor(file: File) : this() {
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

        fun initialize(file: File) = AudioItemJsonRepository(file)
    }
}

val audioItemRepositorySerializersModule = SerializersModule {
    polymorphic(JsonFileRepository::class) {
        subclass(AudioItemJsonRepository.serializer())
    }
    polymorphic(AudioItemBase::class) {
        subclass(ImmutableAudioItem.serializer())
    }
}