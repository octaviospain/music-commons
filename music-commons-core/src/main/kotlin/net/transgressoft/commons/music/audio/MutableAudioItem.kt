package net.transgressoft.commons.music.audio

import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

internal class MutableAudioItem internal constructor(
    override val id: Int,
    override var path: Path,
    override var title: String,
    override val duration: Duration,
    override val bitRate: Int,
    override var artist: Artist,
    override var album: Album,
    override var genre: Genre,
    override var comments: String?,
    override var trackNumber: Short?,
    override var discNumber: Short?,
    override var bpm: Float?,
    override val encoder: String?,
    override val encoding: String?,
    var _coverImage: ByteArray? = null,
    override val dateOfCreation: LocalDateTime = LocalDateTime.now(),
    override var lastDateModified: LocalDateTime = dateOfCreation
) : AudioItemBase(
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

    override fun update(change: AudioItemMetadataChange): MutableAudioItem {
        change.let { theChange ->
            theChange.title?.let { title = it }
            theChange.artist?.let { artist = it }
            theChange.coverImage?.let { _coverImage = it }
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

    override fun toString() = "MutableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}