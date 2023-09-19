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
import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.JsonFileRepository
import net.transgressoft.commons.data.RepositoryBase
import net.transgressoft.commons.data.StandardDataEvent.Type.CREATE
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
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
import kotlin.collections.HashMap

/**
 * @author Octavio Calleya
 */
class AudioItemJsonRepository(file: File) : RepositoryBase<Int, MutableAudioItem>(), AudioItemRepository<MutableAudioItem> {

    private val logger = KotlinLogging.logger(javaClass.name)

    private val idCounter = AtomicInteger(1)
    private val serializableAudioItemsRepository =
        JsonFileRepository(file, Int.serializer(), ImmutableAudioItem.serializer(), audioItemSerializerModule)
    private val artisCatalogAudioItemSubscriber = AudioItemEventSubscriber<MutableAudioItem>("InnerAudioItemSubscriber")

    override val artistCatalogRegistry: ArtistCatalogRegistry = ArtistCatalogInnerRegistry()

    init {
        subscribe(artisCatalogAudioItemSubscriber)
    }

    override fun add(entity: MutableAudioItem) = super.add(entity).also {
        serializableAudioItemsRepository.add(entity.toImmutableAudioItem())
    }

    override fun createFromFile(audioItemPath: Path): MutableAudioItem = MutableAudioItemImpl(newId(), AudioUtils.readAudioItemFields(audioItemPath))

    private fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioItemJsonRepository
        return entitiesById == that.entitiesById && artistCatalogRegistry == that.artistCatalogRegistry
    }

    override fun hashCode() = Objects.hash(entitiesById, artistCatalogRegistry)

    override fun toString() = "AudioItemJsonRepository[entityCount=${entitiesById.size}, albumCount=${artistCatalogRegistry.size()}]"

    internal inner class MutableAudioItemImpl internal constructor(
        override val id: Int,
        audioItemBuilder: AudioItemBuilder<AudioItem>
    ) : MutableAudioItem, AudioItemBase(
        audioItemBuilder.path,
        audioItemBuilder.title,
        audioItemBuilder.duration,
        audioItemBuilder.bitRate,
        audioItemBuilder.artist,
        audioItemBuilder.album,
        audioItemBuilder.genre,
        audioItemBuilder.comments,
        audioItemBuilder.trackNumber,
        audioItemBuilder.discNumber,
        audioItemBuilder.bpm,
        audioItemBuilder.encoder,
        audioItemBuilder.encoding,
        audioItemBuilder.coverImage,
        audioItemBuilder.dateOfCreation,
        audioItemBuilder.lastDateModified
    ) {

        override var coverImage: ByteArray? = audioItemBuilder.coverImage
            get() = field ?: AudioUtils.getCoverBytes(this)

        override var lastDateModified: LocalDateTime = audioItemBuilder.lastDateModified

        override fun update(change: AudioItemMetadataChange): MutableAudioItem {
            change.let { theChange ->
                theChange.title?.let { title = it }
                theChange.artist?.let { artist = it }
                theChange.coverImage?.let { coverImage = it }
                theChange.genre?.let { genre = it }
                theChange.comments?.let { comments = it }
                theChange.trackNumber?.let { trackNumber = it }
                theChange.discNumber?.let { discNumber = it }
                theChange.bpm?.let { bpm = it }
                val newAlbum = ImmutableAlbum(
                    theChange.albumName ?: album.name,
                    theChange.artist ?: album.albumArtist,
                    theChange.isCompilation ?: album.isCompilation,
                    theChange.year ?: album.year,
                    theChange.label ?: album.label
                )
                if (newAlbum != album)
                    album = newAlbum
            }
            lastDateModified = LocalDateTime.now()
            return this
        }

        override fun update(changeAction: AudioItemMetadataChange.() -> Unit): MutableAudioItem =
            AudioItemMetadataChange().let { change ->
                change.changeAction()
                update(change)
            }

        override fun toImmutableAudioItem(): AudioItem =
            ImmutableAudioItem(
                id,
                path,
                title,
                duration,
                bitRate,
                artist,
                album,
                genre,
                comments,
                trackNumber,
                discNumber,
                bpm,
                encoder,
                encoding,
                coverImage,
                dateOfCreation,
                lastDateModified
            )

        override fun toString() = "MutableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
    }

    internal inner class MutableArtistCatalog(override val artist: Artist, override val albums: MutableMap<String, MutableExtendedAlbum> = HashMap()) : ArtistCatalog {

        internal constructor(audioItem: AudioItem) : this(audioItem.artist) {
            audioItem.album.let { album ->
                val newAlbum = MutableExtendedAlbum(album.name, album.albumArtist, album.isCompilation, album.year, album.label)
                newAlbum.audioItems.add(audioItem)
                albums[album.name] = newAlbum
            }
        }

        override val id: String = "${artist.name}-${artist.countryCode.name}"

        override val uniqueId: String = "${artist.name}-${artist.countryCode.name}"

        internal fun addAudioItem(audioItem: AudioItem) {
            albums.merge(audioItem.album.name, MutableExtendedAlbum(audioItem)) { album, _ ->
                album.addAudioItem(audioItem)
                album
            }
        }
    }

    internal inner class MutableExtendedAlbum(
        name: String,
        albumArtist: Artist,
        isCompilation: Boolean,
        year: Short?,
        label: Label,
        override var audioItems: SortedSet<AudioItem> = sortedSetOf(audioItemTrackDiscNumberComparator)
    ) : ExtendedAlbum, ImmutableAlbum(name, albumArtist, isCompilation, year, label) {

        internal constructor(audioItem: AudioItem) : this(audioItem.album.name, audioItem.album.albumArtist, audioItem.album.isCompilation, audioItem.album.year, audioItem.album.label) {
            audioItems.add(audioItem)
        }

        internal fun addAudioItem(audioItem: AudioItem) {
            audioItems.add(audioItem)
        }
    }

    internal inner class ArtistCatalogInnerRegistry :
        ArtistCatalogRegistry,
        RepositoryBase<String, ArtistCatalog>(),
        TransEventSubscriber<MutableAudioItem, DataEvent<out MutableAudioItem>> by artisCatalogAudioItemSubscriber {

        private val artistCatalogsById: MutableMap<String, MutableArtistCatalog> = HashMap()

        init {
            addOnNextEventAction(CREATE) {
                it.entities.forEach(::addAudioItem)
            }
        }

        private fun addAudioItem(audioItem: AudioItem) {
            artistCatalogsById.merge(audioItem.artistUniqueId(), MutableArtistCatalog(audioItem)) { artistCatalog, _ ->
                artistCatalog.addAudioItem(audioItem)
                artistCatalog
            }
        }

        private fun AudioItem.artistUniqueId() = "${artist.name}-${artist.countryCode.name}"

        override fun findFirstByName(artistName: String): Optional<ArtistCatalog> {
            TODO("Not yet implemented")
        }
    }
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