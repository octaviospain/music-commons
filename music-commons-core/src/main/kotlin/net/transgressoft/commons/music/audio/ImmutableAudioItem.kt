package net.transgressoft.commons.music.audio

import com.google.common.base.MoreObjects
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
    constructor(id: Int, attributes: AudioItemAttributes) : this(
        id,
        attributes[AudioItemAttribute.PATH]!!,
        attributes[AudioItemAttribute.TITLE]!!,
        attributes[AudioItemAttribute.DURATION]!!,
        attributes[AudioItemAttribute.BITRATE]!!,
        attributes[AudioItemAttribute.ARTIST]!!,
        attributes[AudioItemAttribute.ALBUM]!!,
        attributes[AudioItemAttribute.GENRE]!!,
        attributes[AudioItemAttribute.COMMENTS],
        attributes[AudioItemAttribute.TRACK_NUMBER],
        attributes[AudioItemAttribute.DISC_NUMBER],
        attributes[AudioItemAttribute.BPM],
        attributes[AudioItemAttribute.ENCODER],
        attributes[AudioItemAttribute.ENCODING],
        attributes[AudioItemAttribute.DATE_OF_CREATION]!!,
        attributes[AudioItemAttribute.LAST_DATE_MODIFIED]!!
    )

    override fun toString(): String =
        MoreObjects.toStringHelper(this)
            .add("path", path)
            .add("name", title)
            .add("artist", artist)
            .toString()
}