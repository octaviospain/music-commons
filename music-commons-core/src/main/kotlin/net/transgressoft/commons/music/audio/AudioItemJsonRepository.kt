package net.transgressoft.commons.music.audio

import mu.KotlinLogging
import java.io.File
import java.nio.file.Path
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

typealias AudioRepository = AudioItemRepository<AudioItem>

/**
 * @author Octavio Calleya
 */
class AudioItemJsonRepository(override val name: String, file: File) : AudioItemRepositoryBase<AudioItem>(file, AudioItemSerializer) {

    private val logger = KotlinLogging.logger {}

    override fun entityClone(entity: AudioItem): AudioItem = MutableAudioItem(entity.path, entity.id)

    override fun createFromFile(audioItemPath: Path): AudioItem =
        MutableAudioItem(audioItemPath, newId())
            .also { audioItem ->
                add(audioItem)
                logger.debug { "New AudioItem was created from file $audioItemPath with id ${audioItem.id}" }
            }

    override fun toString() = "AudioItemJsonRepository(name=$name, audioItemsCount=${entitiesById.size})"
}

val audioItemSerializerModule = SerializersModule {
    polymorphic(Artist::class) {
        subclass(ImmutableArtist.serializer())
    }
    polymorphic(Album::class) {
        subclass(ImmutableAlbum.serializer())
    }
    polymorphic(Label::class) {
        subclass(ImmutableLabel.serializer())
    }
}