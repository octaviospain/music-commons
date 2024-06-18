package net.transgressoft.commons.music.audio

import mu.KotlinLogging
import java.io.File
import java.nio.file.Path

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
