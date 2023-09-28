package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.RepositoryBase
import net.transgressoft.commons.data.StandardDataEvent.Type.*
import net.transgressoft.commons.data.UpdatedDataEvent
import net.transgressoft.commons.data.json.JsonFileRepository
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import com.neovisionaries.i18n.CountryCode
import mu.KotlinLogging
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.*

/**
 * @author Octavio Calleya
 */
class AudioItemJsonRepository(override val name: String, file: File) : RepositoryBase<Int, MutableAudioItem>(),
    AudioItemRepository<MutableAudioItem> {

    private val logger = KotlinLogging.logger(javaClass.name)

    private val serializableAudioItemsRepository =
        JsonFileRepository(file, MapSerializer(Int.serializer(), ImmutableAudioItem.serializer()), audioItemSerializerModule)

    private val audioItemChangesSubscriber = AudioItemEventSubscriber<MutableAudioItem>("$name-AudioItemChangesSubscriber")
        .apply {
            addOnNextEventAction(UPDATE) {
                it as UpdatedDataEvent
                check(it.entitiesById.size == 1) { "An AudioItem is only supposed to publish events of himself alone" }
                addOrReplace(it.entitiesById.values.elementAt(0))
            }
        }

    init {
        serializableAudioItemsRepository.runForAll { serializedAudioItem ->
            InternalMutableAudioItem(serializedAudioItem).apply {
                subscribe(audioItemChangesSubscriber)
                add(this)
            }
        }
    }

    private val idCounter = AtomicInteger(1)

    private val artistCatalogRegistry = ArtistCatalogVolatileRegistry()

    override fun entityClone(entity: MutableAudioItem): MutableAudioItem = InternalMutableAudioItem(entity)

    override fun createFromFile(audioItemPath: Path): MutableAudioItem =
        InternalMutableAudioItem(newId(), readAudioItemFields(audioItemPath))
            .also { audioItem ->
                artistCatalogRegistry.findAlbum(audioItem.album.name, audioItem.artist)
                    .ifPresent { album -> audioItem.album = album }

                // This subscription ensures that when an audio item is updated, the repository is notified
                audioItem.subscribe(audioItemChangesSubscriber)

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

    override fun getArtistCatalog(artist: Artist): Optional<ArtistCatalog> = artistCatalogRegistry.findFirst(artist).map { it as ArtistCatalog }

    override fun getArtistCatalog(artistName: String): Optional<ArtistCatalog> = artistCatalogRegistry.findFirst(artistName).map { it as ArtistCatalog }

    override fun getAlbum(albumName: String, artist: Artist) = artistCatalogRegistry.findAlbum(albumName, artist)

    override fun putCreateEvent(entity: MutableAudioItem) = super.putCreateEvent(entity).also {
        artistCatalogRegistry.addAudioItems(listOf(entity))
    }

    override fun putCreateEvent(entities: Collection<MutableAudioItem>) = super.putCreateEvent(entities).also {
        artistCatalogRegistry.addAudioItems(entities)
    }

    override fun putUpdateEvent(entity: MutableAudioItem, oldEntity: MutableAudioItem) = super.putUpdateEvent(entity, oldEntity).also {
        artistCatalogRegistry.updateCatalog(entity, oldEntity)
    }

    override fun putUpdateEvent(entities: Collection<MutableAudioItem>, oldEntities: Collection<MutableAudioItem>) = super.putUpdateEvent(entities, oldEntities).also {
        entities.forEach {
            val oldEntity = oldEntities.firstOrNull { old -> old.id == it.id } ?: error("Old entity not found for updated one with id ${it.id}")
            artistCatalogRegistry.updateCatalog(it, oldEntity)
        }
    }

    override fun putDeleteEvent(entity: MutableAudioItem) = super.putDeleteEvent(entity).also {
        artistCatalogRegistry.removeAudioItems(listOf(entity))
    }

    override fun putDeleteEvent(entities: Collection<MutableAudioItem>) = super.putDeleteEvent(entities).also {
        artistCatalogRegistry.removeAudioItems(entities)
    }

    override fun clear() = super.clear().also { serializableAudioItemsRepository.clear() }

    override fun containsAudioItemWithArtist(artistName: String) =
        entitiesById.values.any { it.artistsInvolved.stream().map(String::lowercase).toList().contains(artistName.lowercase()) }

    override fun getRandomAudioItemsFromArtist(artistName: String, size: Short, countryCode: CountryCode): List<MutableAudioItem> =
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