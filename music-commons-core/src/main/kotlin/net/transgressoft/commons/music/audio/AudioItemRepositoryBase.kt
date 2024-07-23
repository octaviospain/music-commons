package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.json.JsonFileRepository
import com.neovisionaries.i18n.CountryCode
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule

abstract class AudioItemRepositoryBase<I>(
    name: String,
    file: File,
    audioItemSerializerBase: AudioItemSerializerBase<I>, serializersModule : SerializersModule = SerializersModule {})
    : JsonFileRepository<Int, I>(file, MapSerializer(Int.serializer(), audioItemSerializerBase), SerializersModule {
        include(serializersModule)
        include(audioItemSerializerModule)
    }, name),
    AudioItemRepository<I> where I : ReactiveAudioItem<I> {

    private val artistCatalogRegistry = ArtistCatalogVolatileRegistry<I>()

    private val idCounter = AtomicInteger(1)

    protected fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    override fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I> = artistCatalogRegistry.findAlbumAudioItems(artist, albumName)

    override fun containsAudioItemWithArtist(artistName: String) =
        entitiesById.values.any { it.artistsInvolved.stream().map(String::lowercase).toList().contains(artistName.lowercase()) }

    override fun getRandomAudioItemsFromArtist(artistName: String, size: Short, countryCode: CountryCode): List<I> =
        entitiesById.values
            .asSequence()
            .filter { it.artist.id() == ImmutableArtist.id(artistName, countryCode) }
            .shuffled()
            .take(size.toInt())
            .toList()


    override fun putCreateEvent(entity: I) = super.putCreateEvent(entity).also {
        artistCatalogRegistry.addAudioItems(listOf(entity))
    }

    override fun putCreateEvent(entities: Collection<I>) = super.putCreateEvent(entities).also {
        artistCatalogRegistry.addAudioItems(entities)
    }

    override fun putUpdateEvent(entity: I, oldEntity: I) = super.putUpdateEvent(entity, oldEntity).also {
        artistCatalogRegistry.updateCatalog(entity, oldEntity)
    }

    override fun putUpdateEvent(entities: Collection<I>, oldEntities: Collection<I>) = super.putUpdateEvent(entities, oldEntities).also {
        entities.forEach {
            val oldEntity = oldEntities.firstOrNull { old -> old.id == it.id } ?: error("Old entity not found for updated one with id ${it.id}")
            artistCatalogRegistry.updateCatalog(it, oldEntity)
        }
    }

    override fun putDeleteEvent(entity: I) = super.putDeleteEvent(entity).also {
        artistCatalogRegistry.removeAudioItems(listOf(entity))
    }

    override fun putDeleteEvent(entities: Collection<I>) = super.putDeleteEvent(entities).also {
        artistCatalogRegistry.removeAudioItems(entities)
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioItemRepositoryBase<*>
        return entitiesById == that.entitiesById && artistCatalogRegistry == that.artistCatalogRegistry
    }

    override fun hashCode() = Objects.hash(entitiesById, artistCatalogRegistry)
}
