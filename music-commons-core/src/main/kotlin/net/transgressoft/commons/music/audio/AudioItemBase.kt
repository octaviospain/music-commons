package net.transgressoft.commons.music.audio

import com.google.common.base.Objects
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.query.QueryEntity
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.io.path.extension

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("audioItemType")
abstract class AudioItemBase(
    @Transient override val id: Int = 0,
    @Transient override val path: Path = Path.of(""),
    @Transient override val title: String = "",
    @Transient override val duration: Duration = Duration.ZERO,
    @Transient override val bitRate: Int = 0,
    @Transient override val artist: Artist = ImmutableArtist.UNKNOWN,
    @Transient override val album: Album = ImmutableAlbum.UNKNOWN,
    @Transient override val genre: Genre = Genre.UNDEFINED,
    @Transient override val comments: String? = null,
    @Transient override val trackNumber: Short? = null,
    @Transient override val discNumber: Short? = null,
    @Transient override val bpm: Float? = null,
    @Transient override val encoder: String? = null,
    @Transient override val encoding: String? = null,
    @Transient override val dateOfCreation: LocalDateTime = LocalDateTime.now(),
    @Transient override val lastDateModified: LocalDateTime = dateOfCreation
) : AudioItem, Comparable<AudioItem> {

    override val uniqueId by lazy {
        StringJoiner("-")
            .add(path.fileName.toString().replace(' ', '_'))
            .add(title)
            .add(duration.toSeconds().toString())
            .add(bitRate.toString())
            .toString()
    }

    override val fileName by lazy {
        path.fileName.toString()
    }

    override val extension by lazy {
        path.extension
    }

    override val artistsInvolved by lazy {
        AudioUtils.getArtistsNamesInvolved(title, artist.name, album.albumArtist.name)
    }

    override val length by lazy {
        path.toFile().length()
    }

    override operator fun compareTo(other: AudioItem) = Comparator.comparing(QueryEntity::uniqueId, java.lang.String.CASE_INSENSITIVE_ORDER).compare(this, other)

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
}
