package net.transgressoft.commons.music.audio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.io.File

@Serializable
@SerialName("AudioItemRepository")
class AudioItemJsonRepository(@Transient val _file: File? = null) :
    AudioItemJsonRepositoryBase<AudioItem>(_file, audioItemRepositoryBaseSerializersModule + audioItemRepositorySerializersModule) {

    constructor() : this(null)

    companion object {
        val audioItemRepositorySerializersModule = SerializersModule {
            polymorphic(AudioItemJsonRepositoryBase::class) {
                subclass(AudioItemJsonRepository::class)
            }
        }
    }
}