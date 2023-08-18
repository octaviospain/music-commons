package net.transgressoft.commons.music.audio

import com.google.common.base.Objects
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.transgressoft.commons.music.audio.AudioItemBase.AudioItemBaseBuilder
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * @author Octavio Calleya
 */
@Serializable
@SerialName("DefaultAudioItem")
internal data class ImmutableAudioItem(
    override val id: Int,
    @Serializable(with = PathSerializer::class) override val path: Path,
    override val title: String,
    @Serializable(with = DurationSerializer::class) override val duration: Duration,
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
    @Serializable(with = LocalDateTimeSerializer::class) override val dateOfCreation: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class) override val lastDateModified: LocalDateTime = dateOfCreation
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
) {

    companion object {
        fun createFromFile(audioItemPath: Path): ImmutableAudioItem = ImmutableAudioItemBuilder(readAudioItemFields(audioItemPath).id(UNASSIGNED_ID)).build()
        fun builder() : AudioItemBuilder<AudioItem> = ImmutableAudioItemBuilder()
    }

    override fun toBuilder(): ImmutableAudioItemBuilder = ImmutableAudioItemBuilder(super.toBuilder())

    override fun hashCode() = Objects.hashCode(path, title, artist, album, genre, comments, trackNumber, discNumber, bpm, duration)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ImmutableAudioItem
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

    override fun toString() = "ImmutableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}

internal class ImmutableAudioItemBuilder internal constructor(builder: AudioItemBuilder<AudioItem>?) : AudioItemBaseBuilder(builder) {

    constructor() : this(null)

    override fun build(): ImmutableAudioItem {
        return ImmutableAudioItem(
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

val audioItemSerializerModule = SerializersModule {
    polymorphic(AudioItemBase::class) {
        subclass(ImmutableAudioItem.serializer())
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

object PathSerializer: KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("path", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Path {
        return Paths.get(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.toAbsolutePath().toString())
    }
}

object DurationSerializer: KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("durationSerializer", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Duration {
        return Duration.ofSeconds(decoder.decodeLong())
    }

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeLong(value.seconds)
    }
}

object LocalDateTimeSerializer: KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("localDateTime", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.ofEpochSecond(decoder.decodeLong(), 0, ZoneOffset.UTC)
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeLong(value.toEpochSecond(ZoneOffset.UTC))
    }
}