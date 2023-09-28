package net.transgressoft.commons.music.audio

import net.transgressoft.commons.IdentifiableEntity
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

const val UNASSIGNED_ID = 0

data class AudioItemAttributes(
    val path: Path,
    var title: String,
    val duration: Duration,
    val bitRate: Int,
    var artist: Artist,
    var album: Album,
    var genre: Genre,
    var comments: String? = null,
    var trackNumber: Short? = null,
    var discNumber: Short? = null,
    var bpm: Float? = null,
    val encoder: String? = null,
    val encoding: String? = null,
    var coverImageBytes: ByteArray? = null,
    val dateOfCreation: LocalDateTime,
    val lastDateModified: LocalDateTime,
    override val id: Int = UNASSIGNED_ID
): IdentifiableEntity<Int> {

    override val uniqueId =
        path.fileName.toString().replace(' ', '_').let {
            "$it-$title-${duration.toSeconds()}-$bitRate"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioItemAttributes

        if (path != other.path) return false
        if (title != other.title) return false
        if (duration != other.duration) return false
        if (bitRate != other.bitRate) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (genre != other.genre) return false
        if (comments != other.comments) return false
        if (trackNumber != other.trackNumber) return false
        if (discNumber != other.discNumber) return false
        if (bpm != other.bpm) return false
        if (encoder != other.encoder) return false
        if (encoding != other.encoding) return false
        if (dateOfCreation != other.dateOfCreation) return false
        if (lastDateModified != other.lastDateModified) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + bitRate
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + genre.hashCode()
        result = 31 * result + (comments?.hashCode() ?: 0)
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + (discNumber ?: 0)
        result = 31 * result + (bpm?.hashCode() ?: 0)
        result = 31 * result + (encoder?.hashCode() ?: 0)
        result = 31 * result + (encoding?.hashCode() ?: 0)
        result = 31 * result + dateOfCreation.hashCode()
        result = 31 * result + lastDateModified.hashCode()
        return result
    }
}

internal fun AudioItem.attributes() = AudioItemAttributes(
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
    coverImageBytes,
    dateOfCreation,
    lastDateModified,
    id
)