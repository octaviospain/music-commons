package net.transgressoft.commons.music.audio

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import mu.KotlinLogging
import net.transgressoft.commons.data.JsonFileRepository
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * @author Octavio Calleya
 */
class AudioItemJsonRepository(file: File) :
    JsonFileRepository<ImmutableAudioItem, Int>(file, Int.serializer(), ImmutableAudioItem.serializer(), audioItemSerializerModule), AudioItemRepository<ImmutableAudioItem> {

    private val logger = KotlinLogging.logger(javaClass.name)

    private val idCounter = AtomicInteger(1)

    private val albumsByArtist: MutableMap<Artist, MutableSet<Album>> = mutableMapOf()

    init {
        entitiesById.values.forEach { addOrReplaceAlbumByArtist(it, true) }
    }

    override fun add(entity: ImmutableAudioItem): Boolean {
        val entityToAdd = fillMissingId(entity)

        val added = super.add(entityToAdd)
        addOrReplaceAlbumByArtist(entityToAdd, added)

        return added
    }

    private fun fillMissingId(entity: ImmutableAudioItem) =
        if (entity.id <= UNASSIGNED_ID) {
            entity.toBuilder().id(newId()).build().also {
                logger.debug { "New id ${it.id} assigned to audioItem with uniqueId ${entity.uniqueId}" }
            }
        } else entity

    private fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    private fun addOrReplaceAlbumByArtist(audioItem: ImmutableAudioItem, added: Boolean) {
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

    override fun addOrReplace(entity: ImmutableAudioItem): Boolean {
        val entityToAdd = fillMissingId(entity)
        val addedOrReplaced = super.addOrReplace(entityToAdd)
        addOrReplaceAlbumByArtist(entityToAdd, addedOrReplaced)
        return addedOrReplaced
    }

    override fun addOrReplaceAll(entities: Set<ImmutableAudioItem>): Boolean {
        val entitiesToAdd = entities.map { fillMissingId(it) }.toSet()

        val addedOrReplaced = super.getAddedOrReplacedEntities(entitiesToAdd)
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

    override fun remove(entity: ImmutableAudioItem): Boolean {
        val removed = super.remove(entity)
        removeAlbumByArtistInternal(entity)
        super.serializeToJson()
        return removed
    }

    private fun removeAlbumByArtistInternal(audioItem: ImmutableAudioItem) {
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

    private fun isAlbumNotEmpty(album: Album) = entitiesById.values.any { it.album == album }

    override fun removeAll(entities: Set<ImmutableAudioItem>): Boolean {
        val removed = super.removeAll(entities)
        entities.forEach(::removeAlbumByArtistInternal)
        return removed
    }

    override fun containsAudioItemWithArtist(artistName: String): Boolean {
        return entitiesById.values.any { it.artistsInvolved.stream().map(String::lowercase).toList().contains(artistName.lowercase()) }
    }

    override fun getRandomAudioItemsFromArtist(artist: Artist, size: Int): List<ImmutableAudioItem> {
        return albumsByArtist[artist]?.stream()
            ?.flatMap { albumAudioItems(it).stream() }
            ?.limit(size.toLong())
            ?.collect(Collectors.toList())
            .also { it?.shuffle() } ?: emptyList()
    }

    override fun artists(): Set<Artist> = albumsByArtist.keys.toSet()

    override fun artistAlbums(artist: Artist): Set<Album> = albumsByArtist[artist]?.toSet() ?: emptySet()

    override fun albumAudioItems(album: Album): Set<ImmutableAudioItem> = entitiesById.values.filter { it.album == album }.toSet()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioItemJsonRepository
        return entitiesById == that.entitiesById && albumsByArtist == that.albumsByArtist
    }

    override fun hashCode() = Objects.hash(entitiesById, albumsByArtist)

    override fun toString() = "AudioItemJsonRepository[entityCount=${entitiesById.size}, albumCount=${albumsByArtist.size}]"
}

val audioItemSerializerModule = SerializersModule {
    polymorphic(AudioItem::class) {
        subclass(ImmutableAudioItem::class)
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
    contextual(LocalDateTimeSerializer)
    contextual(DurationSerializer)
    contextual(PathSerializer)
}

object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("path", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Path {
        return Paths.get(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.toAbsolutePath().toString())
    }
}

object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("durationSerializer", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Duration {
        return Duration.ofSeconds(decoder.decodeLong())
    }

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeLong(value.seconds)
    }
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("localDateTime", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.ofEpochSecond(decoder.decodeLong(), 0, ZoneOffset.UTC)
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeLong(value.toEpochSecond(ZoneOffset.UTC))
    }
}