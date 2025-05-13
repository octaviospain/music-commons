package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.StandardCrudEvent.Update
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
import net.transgressoft.commons.persistence.Repository
import mu.KotlinLogging
import java.nio.file.Path
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * @author Octavio Calleya
 */
class DefaultAudioLibrary(repository: Repository<Int, AudioItem>): AudioLibraryBase<AudioItem>(repository) {
    private val logger = KotlinLogging.logger {}

    init {
        playerSubscriber.addOnNextEventAction(PLAYED) { event ->
            val audioItem = event.entities.values.first()
            logger.info { "Audio item with id ${audioItem.id} was played" }
            if (audioItem is MutableAudioItem) {
                val audioItemClone = audioItem.clone()
                audioItem.incrementPlayCount()
                repository.emitAsync(Update(audioItem, audioItemClone))
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

    override fun toString() = "AudioItemJsonRepository(audioItemsCount=${size()})"
}

@get:JvmName("audioItemSerializerModule")
internal val audioItemSerializerModule =
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