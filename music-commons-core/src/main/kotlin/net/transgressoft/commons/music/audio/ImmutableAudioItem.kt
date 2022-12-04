package net.transgressoft.commons.music.audio

import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

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