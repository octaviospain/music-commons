package net.transgressoft.commons.music.audio

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import mu.KotlinLogging
import net.transgressoft.commons.data.JsonFileRepository
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors
import kotlin.streams.toList

/**
 * @author Octavio Calleya
 */
@Serializable
@SerialName("AudioItemRepositoryBase")
abstract class AudioItemJsonRepositoryBase<I : AudioItem> (
    @Transient val file: File? = null,
    @Transient override val repositorySerializersModule: SerializersModule? = null
): JsonFileRepository<I>(file), AudioItemRepository<I> {

    @Transient
    private val logger = KotlinLogging.logger(javaClass.name)

    @Transient
    override var repositorySerializer: KSerializer<*> = serializer(AudioItemBase.serializer())

    @Transient
    private val idCounter = AtomicInteger(1)

    protected val albumsByArtist: MutableMap<Artist, MutableSet<Album>> = mutableMapOf()

    init {
        require(file?.exists()?.and(file.canWrite().and(file.extension == "json")) ?: true) {
            "Provided jsonFile does not exist, is not writable or is not a json file"
        }
        if (jsonFile?.readText()?.isNotEmpty() == true) {
            val decodedRepository = json.decodeFromString(serializer(AudioItemBase.serializer()), file!!.readText())

            @Suppress("UNCHECKED_CAST")
            addOrReplaceAll(decodedRepository.entitiesById.values.map { audioItemBase -> audioItemBase as I }.toSet())
        }
    }

    private fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    @Suppress("UNCHECKED_CAST")
    final override fun add(entity: I): Boolean {
        val entityToAdd = if (entity.id <= UNASSIGNED_ID) {
            entity.toBuilder().id(newId()).build().also {
                logger.debug { "New id ${it.id} assigned to audioItem with uniqueId ${entity.uniqueId}" }
            } as I
        } else entity

        val added = super.add(entityToAdd)
        addOrReplaceAlbumByArtist(entityToAdd, added)

        return added
    }

    private fun addOrReplaceAlbumByArtist(audioItem: I, added: Boolean) {
        val artist = audioItem.artist
        val album = audioItem.album
        if (added) {
            albumsByArtist[artist]?.let {
                val mappedAlbums = albumsByArtist[artist]
                if (!it.contains(album)) {
                    mappedAlbums!!.add(album)
                }
            } ?: run {
                val newSet = HashSet<Album>()
                newSet.add(album)
                albumsByArtist[artist] = newSet
            }
        }
    }

    final override fun addOrReplace(entity: I): Boolean {
        val addedOrReplaced = super.addOrReplace(entity)
        addOrReplaceAlbumByArtist(entity, addedOrReplaced)
        return addedOrReplaced
    }

    final override fun addOrReplaceAll(entities: Set<I>): Boolean {
        val addedOrReplaced = super.getAddedOrReplacedEntities(entities)
        addedOrReplaced[true]?.let { addedList ->
            if (addedList.isNotEmpty()) {
                addedList.forEach(Consumer { addOrReplaceAlbumByArtist(it, true) })
                putCreateEvent(addedList)
            }
        }
        addedOrReplaced[false]?.let { replacedList ->
            if (replacedList.isNotEmpty()) {
                replacedList.forEach(Consumer { addOrReplaceAlbumByArtist(it, true) })
                putUpdateEvent(replacedList)
            }
        }
        return addedOrReplaced.values.stream().flatMap { it.stream() }.findAny().isPresent
    }

    final override fun remove(entity: I): Boolean {
        val removed = super.remove(entity)
        removeAlbumByArtistInternal(entity)
        super.serializeToJson()
        return removed
    }

    private fun removeAlbumByArtistInternal(audioItem: I) {
        val artist = audioItem.artist
        if (albumsByArtist.containsKey(artist)) {
            var albums = albumsByArtist[audioItem.artist]
            albums = albums?.stream()?.filter { album: Album -> isAlbumNotEmpty(album) }?.collect(Collectors.toSet())
            if (albums != null) {
                if (albums.isEmpty()) {
                    albumsByArtist.remove(artist)
                } else {
                    albumsByArtist[artist] = albums
                }
            }
        }
    }

    private fun isAlbumNotEmpty(album: Album) = searchInternal { it.album == album }.isNotEmpty()

    final override fun removeAll(entities: Set<I>): Boolean {
        val removed = super.removeAll(entities)
        entities.forEach { audioItem: I -> removeAlbumByArtistInternal(audioItem) }
        return removed
    }

    final override fun containsAudioItemWithArtist(artistName: String): Boolean {
        return searchInternal { it.artistsInvolved.stream().map(String::lowercase).toList().contains(artistName.lowercase()) }.isNotEmpty()
    }

    final override fun getRandomAudioItemsFromArtist(artist: Artist, size: Int): List<I> {
        return albumsByArtist[artist]?.stream()
            ?.flatMap { albumAudioItems(it).stream() }
            ?.limit(size.toLong())
            ?.collect(Collectors.toList())
            .also { it?.shuffle() } ?: emptyList()
    }

    final override fun artists(): Set<Artist> = albumsByArtist.keys.toSet()

    final override fun artistAlbums(artist: Artist): Set<Album> = albumsByArtist[artist]?.toSet() ?: emptySet()

    final override fun albumAudioItems(album: Album): Set<I> = searchInternal { it.album == album }.toSet()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioItemJsonRepositoryBase<*>
        return entitiesById == that.entitiesById && albumsByArtist == that.albumsByArtist
    }

    override fun hashCode() = Objects.hash(entitiesById, albumsByArtist)

    override fun toString() = "AudioItemJsonRepository[entityCount=${entitiesById.size}, albumCount=${albumsByArtist.size}]"

    companion object {
        val audioItemRepositoryBaseSerializersModule = SerializersModule {
            polymorphic(TransEventSubscriberBase::class) {
                subclass(AudioItemEventSubscriber.serializer(AudioItemBase.serializer()))
            }
            include(audioItemSerializerModule)
        }
    }
}