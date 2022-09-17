package net.transgressoft.commons.music.audio

import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import net.transgressoft.commons.music.audio.AlbumAttribute.ALBUM
import net.transgressoft.commons.music.audio.ArtistAttribute.ARTIST
import net.transgressoft.commons.music.audio.ArtistsInvolvedAttribute.ARTISTS_INVOLVED
import net.transgressoft.commons.music.audio.AudioItemDurationAttribute.DURATION
import net.transgressoft.commons.music.audio.AudioItemFloatAttribute.BPM
import net.transgressoft.commons.music.audio.AudioItemGenreAttribute.GENRE
import net.transgressoft.commons.music.audio.AudioItemIntegerAttribute.BITRATE
import net.transgressoft.commons.music.audio.AudioItemLocalDateTimeAttribute.DATE_OF_CREATION
import net.transgressoft.commons.music.audio.AudioItemLocalDateTimeAttribute.LAST_DATE_MODIFIED
import net.transgressoft.commons.music.audio.AudioItemPathAttribute.PATH
import net.transgressoft.commons.music.audio.AudioItemShortAttribute.DISC_NUMBER
import net.transgressoft.commons.music.audio.AudioItemShortAttribute.TRACK_NUMBER
import net.transgressoft.commons.music.audio.AudioItemStringAttribute.*
import net.transgressoft.commons.query.QueryEntity
import java.nio.file.Path
import java.util.*
import kotlin.io.path.extension

/**
 * @author Octavio Calleya
 */
class ImmutableAudioItem(override val id: Int, override val attributes: AudioItemAttributes) : AudioItem, Comparable<AudioItem> {

    private val path = attributes[PATH]!!
    private val title = attributes[TITLE]!!
    private val artist = attributes[ARTIST]!!
    private val artistsInvolved = attributes[ARTISTS_INVOLVED]!!
    private val album = attributes[ALBUM]!!
    private val genre = attributes[GENRE]!!
    private val comments = attributes[COMMENTS]
    private val trackNumber = attributes[TRACK_NUMBER]
    private val discNumber = attributes[DISC_NUMBER]
    private val bpm = attributes[BPM]
    private val duration = attributes[DURATION]!!
    private val bitRate = attributes[BITRATE]!!
    private val encoder = attributes[ENCODER]
    private val encoding = attributes[ENCODING]
    private val dateOfCreation = attributes[DATE_OF_CREATION]!!
    private val lastDateModified = attributes[LAST_DATE_MODIFIED]!!

    override val uniqueId: String
        get() = StringJoiner("-")
            .add(fileName().replace(' ', '_'))
            .add(title)
            .add(duration.toString())
            .add(bitRate().toString())
            .toString()

    override fun path() = path

    override fun path(path: Path) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(PATH, path))

    override fun fileName() = path.fileName.toString()

    override fun extension() = path.extension

    override fun title() = title

    override fun title(title: String) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(TITLE, title))

    override fun artist() = artist

    override fun artist(artist: Artist) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(ARTIST, artist))

    override fun artistsInvolved() = artistsInvolved.toSet()

    override fun album(): Album = album

    override fun album(album: Album) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(ALBUM, album))

    override fun genre() = genre

    override fun genre(genre: Genre) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(GENRE_NAME, genre.name))

    override fun comments() = comments

    override fun comments(comments: String) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(COMMENTS, comments))

    override fun trackNumber() = trackNumber

    override fun trackNumber(trackNumber: Short) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(TRACK_NUMBER, trackNumber))

    override fun discNumber() = discNumber

    override fun discNumber(discNumber: Short) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(DISC_NUMBER, discNumber))

    override fun bpm() = bpm

    override fun bpm(bpm: Float) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(BPM, bpm))

    override fun duration() = duration

    override fun length() = path.toFile().length()

    override fun bitRate() = bitRate

    override fun encoder() = encoder

    override fun encoder(encoder: String) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(ENCODER, encoder))

    override fun encoding() = encoding

    override fun encoding(encoding: String) = ImmutableAudioItem(id, attributes.modifiedCopyWithModifiedTime(ENCODING, encoding))

    override fun dateOfInclusion() = dateOfCreation

    override fun lastDateModified() = lastDateModified

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
