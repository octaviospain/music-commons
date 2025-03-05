package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
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
class AudioItemJsonRepository(
    name: String,
    file: File
): AudioItemRepositoryBase<AudioItem>(name, file, AudioItemSerializer) {
    private val logger = KotlinLogging.logger {}

    init {
        playerSubscriber.addOnNextEventAction(PLAYED) { event ->
            val audioItem = event.entities.values.first()
            logger.info { "Audio item with id ${audioItem.id} was played" }
            if (audioItem is MutableAudioItem) {
                val audioItemClone = audioItem.clone()
                audioItem.incrementPlayCount()
                putUpdateEvent(audioItem, audioItemClone)
                logger.debug { "Play count for audio item ${audioItem.id} increased to ${audioItem.playCount}" }
            }
        }
    }

    override fun createFromFile(audioItemPath: Path): AudioItem =
        MutableAudioItem(audioItemPath, newId())
            .also { audioItem ->
                add(audioItem)
                logger.debug { "New AudioItem was created from file $audioItemPath with id ${audioItem.id}" }
            }

    override fun toString() = "AudioItemJsonRepository(name=$name, audioItemsCount=${entitiesById.size})"
}

val audioItemSerializerModule =
    SerializersModule {
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