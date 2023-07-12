package net.transgressoft.commons.music.audio

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

/**
 * @author Octavio Calleya
 */
@Serializable
@SerialName("AudioItem")
internal data class ImmutableAudioItem(
    override val id: Int,
    override val path: Path,
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
    dateOfCreation,
    lastDateModified
) {

    override fun toString() = "ImmutableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}

object DurationSerializer: KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Duration", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Duration {
        return Duration.ofSeconds(decoder.decodeLong())
    }

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeLong(value.seconds)
    }
}

object LocalDateTimeSerializer: KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.ofEpochSecond(decoder.decodeLong(), 0, null)
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeLong(value.toEpochSecond(null))
    }
}