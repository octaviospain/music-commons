package net.transgressoft.commons.music.audio

import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import net.transgressoft.commons.music.audio.AudioItemAttribute.*
import net.transgressoft.commons.query.Attribute
import net.transgressoft.commons.query.QueryEntity
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.io.path.extension

/**
 * @author Octavio Calleya
 */
data class ImmutableAudioItem(
    override val id: Int,
    override val path: Path,
    override val title: String,
    override val duration: Duration,
    override val bitRate: Int,
    override val artist: Artist = ImmutableArtist.UNKNOWN,
    override val album: Album = ImmutableAlbum.UNKNOWN,
    override val genre: Genre = Genre.UNDEFINED,
    override val comments: String? = null,
    override val trackNumber: Short? = null,
    override val discNumber: Short? = null,
    override val bpm: Float? = null,
    override val encoder: String? = null,
    override val encoding: String? = null,
    override val dateOfCreation: LocalDateTime = LocalDateTime.now(),
    override val lastDateModified: LocalDateTime = dateOfCreation
) : AudioItem, Comparable<AudioItem> {

    constructor(id: Int, attributes: AudioItemAttributes) : this(
        id,
        attributes[PATH]!!,
        attributes[TITLE]!!,
        attributes[DURATION]!!,
        attributes[BITRATE]!!,
        attributes[ARTIST]!!,
        attributes[ALBUM]!!,
        attributes[GENRE]!!,
        attributes[COMMENTS],
        attributes[TRACK_NUMBER],
        attributes[DISC_NUMBER],
        attributes[BPM],
        attributes[ENCODER],
        attributes[ENCODING],
        attributes[DATE_OF_CREATION]!!,
        attributes[LAST_DATE_MODIFIED]!!
    )

    override val uniqueId = StringJoiner("-")
            .add(path.fileName.toString().replace(' ', '_'))
            .add(title)
            .add(duration.toString())
            .add(bitRate.toString())
            .toString()

    override val fileName = path.fileName.toString()

    override val extension = path.extension

    override val artistsInvolved = AudioItemUtils.getArtistsNamesInvolved(title, artist.name, album.albumArtist.name)

    override val length = path.toFile().length()

    private val attributes: AudioItemAttributes = AudioItemAttributes(this)

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

    @Suppress("UNCHECKED_CAST")
    override fun <A : Attribute<E, V>, E : QueryEntity, V : Any> get(attribute: A): V? = attributes[attribute as Attribute<AudioItem, V>]

    override operator fun compareTo(other: AudioItem) =
        Comparator.comparing(QueryEntity::uniqueId, java.lang.String.CASE_INSENSITIVE_ORDER).compare(this, other)

    override fun hashCode(): Int =
        Objects.hashCode(path, title, artist, album, genre, comments, trackNumber, discNumber, bpm, duration)

    override fun toString(): String =
        MoreObjects.toStringHelper(this)
            .add("path", path)
            .add("name", title)
            .add("artist", artist)
            .toString()
}
