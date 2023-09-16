package net.transgressoft.commons.music.audio

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.transgressoft.commons.music.AudioUtils
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

/**
 * @author Octavio Calleya
 */
@Serializable
@SerialName("DefaultAudioItem")
class ImmutableAudioItem internal constructor(
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
        fun createFromFile(audioItemPath: Path): ImmutableAudioItem = ImmutableAudioItemBuilder(AudioUtils.readAudioItemFields(audioItemPath)).build()
    }

    override fun update(change: AudioItemMetadataChange): ImmutableAudioItem =
        toBuilder().also {
            it.title = change.title ?: title
            it.artist = change.artist ?: artist
            it.album = ImmutableAlbum(
                change.albumName ?: album.name,
                change.albumArtist ?: album.albumArtist,
                change.isCompilation ?: album.isCompilation,
                change.year?.takeIf { year -> year > 0 } ?: album.year,
                change.label ?: album.label
            )
            it.genre = change.genre ?: genre
            it.comments = change.comments ?: comments
            it.trackNumber = change.trackNumber?.takeIf { trackNum -> trackNum > 0 } ?: trackNumber
            it.discNumber = change.discNumber?.takeIf { discNum -> discNum > 0 } ?: discNumber
            it.bpm = change.bpm ?: bpm
            it.coverImage = change.coverImage ?: coverImage
            it.lastDateModified = LocalDateTime.now()
        }.build()

    override fun update(changeAction: AudioItemMetadataChange.() -> Unit): ImmutableAudioItem =
        AudioItemMetadataChange().let { change ->
            change.changeAction()
            update(change)
        }

    override fun toString() = "ImmutableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}
