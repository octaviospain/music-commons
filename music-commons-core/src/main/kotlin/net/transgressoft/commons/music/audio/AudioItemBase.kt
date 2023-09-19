package net.transgressoft.commons.music.audio

import com.google.common.base.Objects
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import net.transgressoft.commons.music.AudioUtils
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.io.path.extension

const val UNASSIGNED_ID = 0

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("audioItemType")
abstract class AudioItemBase(
    @Transient override val path: Path = Path.of(""),
    @Transient override val title: String = "",
    @Transient override val duration: Duration = Duration.ZERO,
    @Transient override val bitRate: Int = 0,
    @Transient override val artist: Artist = ImmutableArtist.UNKNOWN,
    @Transient override val album: Album = ImmutableAlbum.UNKNOWN,
    @Transient override val genre: Genre = Genre.UNDEFINED,
    @Transient override val comments: String? = null,
    @Transient override val trackNumber: Short? = null,
    @Transient override val discNumber: Short? = null,
    @Transient override val bpm: Float? = null,
    @Transient override val encoder: String? = null,
    @Transient override val encoding: String? = null,
    @Transient private val initialCoverImage: ByteArray? = null,
    @Transient override val dateOfCreation: LocalDateTime = LocalDateTime.now(),
    @Transient override val lastDateModified: LocalDateTime = dateOfCreation
) : AudioItem, Comparable<AudioItem> {

    @Transient override val id: Int = UNASSIGNED_ID

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

    override fun toBuilder(): AudioItemBuilder<out AudioItem> = ImmutableAudioItemBuilder(this)

    override suspend fun writeMetadata() = JAudioTaggerMetadataWriter().writeMetadata(this)

    override operator fun compareTo(other: AudioItem) = audioItemTrackDiscNumberComparator.compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioItemBase
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
}

internal val audioItemTrackDiscNumberComparator = Comparator<AudioItem> { audioItem1, audioItem2 ->
    when {
        audioItem1.discNumber == null && audioItem2.discNumber == null -> {
            // Both discNumbers are null, compare by trackNumber
            when {
                audioItem1.trackNumber == null && audioItem2.trackNumber == null -> 0
                audioItem1.trackNumber == null -> 1
                audioItem2.trackNumber == null -> -1
                else -> audioItem1.trackNumber!! - audioItem2.trackNumber!!
            }
        }
        audioItem1.discNumber == null -> 1
        audioItem2.discNumber == null -> -1
        else -> {
            // Compare non-null discNumbers
            if (audioItem1.discNumber == audioItem2.discNumber) {
                // If discNumbers are equal, compare by trackNumber
                when {
                    audioItem1.trackNumber == null && audioItem2.trackNumber == null -> 0
                    audioItem1.trackNumber == null -> 1
                    audioItem2.trackNumber == null -> -1
                    else -> audioItem1.trackNumber!! - audioItem2.trackNumber!!
                }
            } else {
                // Different discNumbers, compare by discNumber
                audioItem1.discNumber!! - audioItem2.discNumber!!
            }
        }
    }
}