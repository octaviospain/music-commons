package net.transgressoft.commons.music.audio

import com.google.common.base.Objects
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

@Serializable
@SerialName("ExtendedAudioItemRepository")
class ExtendedAudioItemRepository internal constructor(
    @Transient val _file: File? = null
): AudioItemJsonRepositoryBase<ExtendedAudioItemInterface>(_file) {

    @Transient
    override var repositorySerializersModule = audioItemRepositoryBaseSerializersModule + extendedAudioItemRepositorySerializersModule

    constructor() : this(null)

    companion object {
        private val json = Json {
            serializersModule = audioItemRepositoryBaseSerializersModule + extendedAudioItemRepositorySerializersModule
            allowStructuredMapKeys = true
        }

        fun loadFromFile(file: File): ExtendedAudioItemRepository {
            require(file.exists().and(file.canRead().and(file.canWrite()))) {
                "Provided jsonFile does not exist or is not writable"
            }
            return json.decodeFromString(serializer(AudioItemBase.serializer()), file.readText()) as ExtendedAudioItemRepository
        }

        fun initialize(file: File) = ExtendedAudioItemRepository(file)
    }
}

val extendedAudioItemRepositorySerializersModule = SerializersModule {
    polymorphic(AudioItemJsonRepositoryBase::class) {
        subclass(ExtendedAudioItemRepository::class)
    }
    polymorphic(ExtendedAudioItemInterface::class) {
        subclass(ExtendedAudioItem::class)
    }
}

interface ExtendedAudioItemInterface : AudioItem

@Serializable
@SerialName("ExtendedAudioItem")
class ExtendedAudioItem internal constructor(
    override val id: Int,
    @Contextual override val path: Path,
    override val title: String,
    @Contextual override val duration: Duration,
    override val bitRate: Int,
    override val artist: Artist,
    override val album: Album,
    override val genre: Genre,
    override val comments: String?,
    override val trackNumber: Short?,
    override val discNumber: Short?,
    override val bpm: Float?,
    override val encoder: String?,
    override val encoding: String?,
    @Transient val _coverImage: ByteArray? = null,
    @Contextual override val dateOfCreation: LocalDateTime = LocalDateTime.now(),
    @Contextual override val lastDateModified: LocalDateTime = dateOfCreation
) : AudioItemBase(
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
    _coverImage,
    dateOfCreation,
    lastDateModified
), ExtendedAudioItemInterface {

    companion object {
        fun createFromFile(audioItemPath: Path): ExtendedAudioItem = ExtendedAudioItemBuilder(readAudioItemFields(audioItemPath).id(UNASSIGNED_ID)).build()
    }

    override fun toBuilder(): ExtendedAudioItemBuilder = ExtendedAudioItemBuilder(super.toBuilder())

    override fun hashCode() = Objects.hashCode(path, title, artist, album, genre, comments, trackNumber, discNumber, bpm, duration)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ExtendedAudioItem
        return trackNumber == that.trackNumber &&
                discNumber == that.discNumber &&
                bpm == that.bpm &&
                path == that.path &&
                title == that.title &&
                artist == that.artist &&
                album == that.album &&
                genre === that.genre &&
                comments == that.comments &&
                duration == that.duration
    }

    override fun toString() = "ExtendedAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}

class ExtendedAudioItemBuilder internal constructor(builder: AudioItemBuilder<AudioItem>?): AudioItemBase.AudioItemBaseBuilder(builder) {

    constructor() : this(null)

    override fun build(): ExtendedAudioItem {
        return ExtendedAudioItem(
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
    }
}