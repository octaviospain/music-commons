package net.transgressoft.commons.music.audio

import com.google.common.base.Objects
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

internal class InternalMutableAudioItem internal constructor(id: Int, attributes: AudioItemAttributes) :
    MutableAudioItem, MutableAudioItemBase(id, attributes) {

    internal constructor(audioItem: AudioItem) : this(audioItem.id, audioItem.attributes())

    internal constructor(
        id: Int,
        path: Path,
        title: String,
        duration: Duration,
        bitRate: Int,
        artist: Artist,
        album: Album,
        genre: Genre,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        encoding: String?,
        coverImageBytes: ByteArray?,
        dateOfCreation: LocalDateTime,
        lastDateModified: LocalDateTime
    ) : this(id, AudioItemAttributes(path, title, duration, bitRate, artist, album, genre, comments, trackNumber, discNumber, bpm, encoder, encoding, coverImageBytes, dateOfCreation, lastDateModified))

    override fun update(change: AudioItemChange): MutableAudioItem {
        change.let { theChange ->
            theChange.title?.let { title = it }
            theChange.artist?.let { artist = it }
            theChange.coverImageBytes?.let { coverImageBytes = it }
            theChange.genre?.let { genre = it }
            theChange.comments?.let { comments = it }
            theChange.trackNumber?.takeIf { it > 0 }.let { trackNumber = it }
            theChange.discNumber?.takeIf { it > 0 }.let { discNumber = it }
            theChange.bpm?.takeIf { it > 0 }.let { bpm = it }
            val newAlbum = ImmutableAlbum(
                theChange.albumName ?: album.name,
                theChange.albumArtist ?: album.albumArtist,
                theChange.isCompilation ?: album.isCompilation,
                theChange.year?.takeIf { it > 0 } ?: album.year,
                theChange.label ?: album.label
            )
            if (newAlbum != album)
                album = newAlbum
        }
        lastDateModified = LocalDateTime.now()
        return this
    }

    override fun update(changeAction: AudioItemChange.() -> Unit): MutableAudioItem =
        AudioItemChange(id).let { change ->
            change.changeAction()
            update(change)
        }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as InternalMutableAudioItem
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

    override fun hashCode() = Objects.hashCode(path, title, artist, album, genre, comments, trackNumber, discNumber, bpm, duration)

    override fun toString() = "MutableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}