package net.transgressoft.commons.music.audio

import com.google.common.base.Objects
import java.time.LocalDateTime

internal class InternalMutableAudioItem internal constructor(audioItemBuilder: AudioItemBuilder<out AudioItem>) : MutableAudioItem,
    MutableAudioItemBase(
        audioItemBuilder.id,
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

    internal constructor(audioItem: AudioItem) : this(audioItem.toBuilder())

    override fun update(change: AudioItemMetadataChange): MutableAudioItem {
        change.let { theChange ->
            theChange.title?.let { title = it }
            theChange.artist?.let { artist = it }
            theChange.coverImage?.let { coverImage = it }
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

    override fun update(changeAction: AudioItemMetadataChange.() -> Unit): MutableAudioItem =
        AudioItemMetadataChange().let { change ->
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