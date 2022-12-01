package net.transgressoft.commons.music.audio

import com.google.common.base.Objects
import net.transgressoft.commons.query.QueryEntity
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.io.path.extension

abstract class AudioItemBase(
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

    override val uniqueId by lazy {
        StringJoiner("-")
            .add(path.fileName.toString().replace(' ', '_'))
            .add(title)
            .add(duration.toString())
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
        AudioItemUtils.getArtistsNamesInvolved(title, artist.name, album.albumArtist.name)
    }

    override val length by lazy {
        path.toFile().length()
    }

    override fun update(change: AudioItemMetadataChange): AudioItem {
        val updatedTitle = change.title?: title
        val updatedArtist = change.artist?: artist

        val updatedAlbumName = change.albumName?: album.name
        val updatedAlbumArtist = change.albumArtist?: album.albumArtist
        val updatedIsCompilation = change.isCompilation?: album.isCompilation
        val updatedYear = change.year?: album.year
        val updatedLabel = change.label?: album.label
        val updatedCoverImage = change.coveImage?: album.coverImage
        val updatedAlbum = ImmutableAlbum(updatedAlbumName, updatedAlbumArtist, updatedIsCompilation, updatedYear, updatedLabel, updatedCoverImage)

        val updatedGenre = change.genre?: genre
        val updatedComments = change.comments?: comments
        val updatedTrackNumber = change.trackNumber?: trackNumber
        val updatedDiscNumber = change.discNumber?: discNumber
        val updatedBpm = change.bpm?: bpm

        return ImmutableAudioItem(
            id, path, updatedTitle, duration, bitRate, updatedArtist, updatedAlbum, updatedGenre, updatedComments,
            updatedTrackNumber,updatedDiscNumber, updatedBpm, encoder, encoding, dateOfCreation, LocalDateTime.now()
        )
    }

    override operator fun compareTo(other: AudioItem) =
        Comparator.comparing(QueryEntity::uniqueId, java.lang.String.CASE_INSENSITIVE_ORDER).compare(this, other)

    override fun hashCode(): Int =
        Objects.hashCode(path, title, artist, album, genre, comments, trackNumber, discNumber, bpm, duration)

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