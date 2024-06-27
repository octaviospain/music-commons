package net.transgressoft.commons.fx.music

import net.transgressoft.commons.music.audio.AudioItemRepositoryBase
import mu.KotlinLogging
import java.io.File
import java.nio.file.Path
import kotlinx.serialization.modules.SerializersModule

class ObservableAudioItemJsonRepository(override val name: String, file: File) :
    AudioItemRepositoryBase<ObservableAudioItem>(file, ObservableAudioItemSerializer, SerializersModule {
        include(observableAudioItemSerializerModule)
    }) {

    private val logger = KotlinLogging.logger {}

    override fun entityClone(entity: ObservableAudioItem): FXAudioItem = FXAudioItem(entity.path, entity.id)

    override fun createFromFile(audioItemPath: Path): FXAudioItem =
        FXAudioItem(audioItemPath, newId())
            .also { fxAudioItem ->
                add(fxAudioItem)
                logger.debug { "New ObservableAudioItem was created from file $audioItemPath with id ${fxAudioItem.id}" }
            }

    override fun toString() = "ObservableAudioItemJsonRepository(name=$name, audioItemsCount=${entitiesById.size})"
}
