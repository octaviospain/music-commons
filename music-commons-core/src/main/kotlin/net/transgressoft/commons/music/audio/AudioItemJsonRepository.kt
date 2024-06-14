package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.RepositoryBase
import net.transgressoft.commons.data.json.JsonFileRepository
import com.neovisionaries.i18n.CountryCode
import mu.KotlinLogging
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * @author Octavio Calleya
 */
class AudioItemJsonRepository(override val name: String, file: File) : RepositoryBase<Int, AudioItem>(),
    AudioItemRepository<AudioItem> {

    private val logger = KotlinLogging.logger(javaClass.name)

    private val serializableAudioItemsRepository =
        JsonFileRepository(file, MapSerializer(Int.serializer(), MutableAudioItem.serializer()), audioItemSerializerModule)

    init {
        serializableAudioItemsRepository.runForAll { serializedAudioItem ->
            MutableAudioItem(serializedAudioItem.path, serializedAudioItem.id).apply {
                add(this)
            }
        }
    }

    private val idCounter = AtomicInteger(1)

    private val artistCatalogRegistry = ArtistCatalogVolatileRegistry()

    override fun entityClone(entity: AudioItem): AudioItem = MutableAudioItem(entity.path, entity.id)

    override fun createFromFile(audioItemPath: Path): AudioItem =
        MutableAudioItem(audioItemPath, newId())
            .also { audioItem ->
                add(audioItem)
                logger.debug { "New AudioItem was created from file $audioItemPath with id ${audioItem.id}" }
            }

    private fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    override fun add(entity: AudioItem) = super.add(entity).also {
        serializableAudioItemsRepository.add(entity as MutableAudioItem)
    }

    override fun addOrReplace(entity: AudioItem) = super.addOrReplace(entity).also {
        serializableAudioItemsRepository.addOrReplace(entity as MutableAudioItem)
    }

    override fun addOrReplaceAll(entities: Set<AudioItem>) = super.addOrReplaceAll(entities).also {
        serializableAudioItemsRepository.addOrReplaceAll(entities.map { it as MutableAudioItem }.toSet())
    }

    override fun remove(entity: AudioItem) = super.remove(entity).also {
        serializableAudioItemsRepository.remove(entity as MutableAudioItem)
    }

    override fun removeAll(entities: Set<AudioItem>) = super.removeAll(entities).also {
        serializableAudioItemsRepository.removeAll(entities.map { it as MutableAudioItem }.toSet())
    }

    override fun findAlbumAudioItems(artist: Artist, albumName: String): Set<AudioItem> = artistCatalogRegistry.findAlbumAudioItems(artist, albumName)

    override fun putCreateEvent(entity: AudioItem) = super.putCreateEvent(entity).also {
        artistCatalogRegistry.addAudioItems(listOf(entity))
    }

    override fun putCreateEvent(entities: Collection<AudioItem>) = super.putCreateEvent(entities).also {
        artistCatalogRegistry.addAudioItems(entities)
    }

    override fun putUpdateEvent(entity: AudioItem, oldEntity: AudioItem) = super.putUpdateEvent(entity, oldEntity).also {
        artistCatalogRegistry.updateCatalog(entity, oldEntity)
    }

    override fun putUpdateEvent(entities: Collection<AudioItem>, oldEntities: Collection<AudioItem>) = super.putUpdateEvent(entities, oldEntities).also {
        entities.forEach {
            val oldEntity = oldEntities.firstOrNull { old -> old.id == it.id } ?: error("Old entity not found for updated one with id ${it.id}")
            artistCatalogRegistry.updateCatalog(it, oldEntity)
        }
    }

    override fun putDeleteEvent(entity: AudioItem) = super.putDeleteEvent(entity).also {
        artistCatalogRegistry.removeAudioItems(listOf(entity))
    }

    override fun putDeleteEvent(entities: Collection<AudioItem>) = super.putDeleteEvent(entities).also {
        artistCatalogRegistry.removeAudioItems(entities)
    }

    override fun clear() = super.clear().also { serializableAudioItemsRepository.clear() }

    override fun containsAudioItemWithArtist(artistName: String) =
        entitiesById.values.any { it.artistsInvolved.stream().map(String::lowercase).toList().contains(artistName.lowercase()) }

    override fun getRandomAudioItemsFromArtist(artistName: String, size: Short, countryCode: CountryCode): List<AudioItem> =
        entitiesById.values
            .asSequence()
            .filter { it.artist.id() == ImmutableArtist.id(artistName, countryCode) }
            .shuffled()
            .take(size.toInt())
            .toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioItemJsonRepository
        return entitiesById == that.entitiesById && artistCatalogRegistry == that.artistCatalogRegistry
    }

    override fun hashCode() = Objects.hash(entitiesById, artistCatalogRegistry)

    override fun toString() = "AudioItemJsonRepository(name=$name, audioItemsCount=${entitiesById.size})"
}

internal val audioItemSerializerModule = SerializersModule {
    polymorphic(AudioItem::class) {
        subclass(MutableAudioItem::class)
    }
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