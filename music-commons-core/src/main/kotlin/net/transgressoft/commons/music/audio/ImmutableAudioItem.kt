package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.AudioUtils
import com.google.common.base.Objects
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlin.io.path.extension
import kotlinx.serialization.*

/**
 * @author Octavio Calleya
 */
@Serializable
@SerialName("DefaultAudioItem")
internal class ImmutableAudioItem internal constructor(
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
    @Transient val initialCoverImage: ByteArray? = null,
    @Contextual override val dateOfCreation: LocalDateTime = LocalDateTime.now(),
    @Contextual override val lastDateModified: LocalDateTime = dateOfCreation
) : AudioItem, Comparable<AudioItem> {

    companion object {
        fun createFromFile(audioItemPath: Path): AudioItem = ImmutableAudioItemBuilder(AudioUtils.readAudioItemFields(audioItemPath)).build()
    }

    @Transient
    override val coverImage: ByteArray? = initialCoverImage
        get() = field ?: AudioUtils.getCoverBytes(this)

    override val uniqueId by lazy {
        val fileName = path.fileName.toString().replace(' ', '_')
        "$fileName-$title-${duration.toSeconds()}-$bitRate"
    }

    override val fileName by lazy {
        path.fileName.toString()
    }

    override val extension by lazy {
        path.extension
    }

    override val artistsInvolved by lazy {
        AudioUtils.getArtistsNamesInvolved(title, artist.name, album.albumArtist.name)
    }

    override val length by lazy {
        path.toFile().length()
    }

    override fun update(change: AudioItemMetadataChange): AudioItem =
        ImmutableAudioItemBuilder(this).also {
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

    override fun update(changeAction: AudioItemMetadataChange.() -> Unit): AudioItem =
        AudioItemMetadataChange().let { change ->
            change.changeAction()
            update(change)
        }

    override fun toBuilder(): AudioItemBuilder<out AudioItem> = ImmutableAudioItemBuilder(this)

    override suspend fun writeMetadata() = JAudioTaggerMetadataWriter().writeMetadata(this)

    override operator fun compareTo(other: AudioItem) = AudioUtils.audioItemTrackDiscNumberComparator.compare(this, other)

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
                genre == that.genre &&
                comments == that.comments &&
                duration == that.duration
    }

    override fun hashCode() = Objects.hashCode(path, title, artist, album, genre, comments, trackNumber, discNumber, bpm, duration)

    internal fun toInternalMutableAudioItem(): MutableAudioItem = InternalMutableAudioItem(toBuilder())

    override fun toString() = "ImmutableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}
