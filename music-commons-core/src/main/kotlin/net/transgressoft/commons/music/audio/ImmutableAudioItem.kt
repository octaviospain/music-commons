package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.AudioUtils
import com.google.common.base.Objects
import com.neovisionaries.i18n.CountryCode
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.nio.file.Files
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
    @Contextual override val lastDateModified: LocalDateTime = LocalDateTime.now()
) : AudioItem, Comparable<AudioItem> {

    internal constructor(id: Int, attributes: AudioItemAttributes) : this(id,
        attributes.path,
        attributes.title,
        attributes.duration,
        attributes.bitRate,
        attributes.artist,
        attributes.album,
        attributes.genre,
        attributes.comments,
        attributes.trackNumber,
        attributes.discNumber,
        attributes.bpm,
        attributes.encoder,
        attributes.encoding,
        attributes.coverImageBytes,
        attributes.dateOfCreation,
        attributes.lastDateModified)

    companion object {
        fun createFromFile(audioItemPath: Path): AudioItem = ImmutableAudioItem(UNASSIGNED_ID, readAudioItemFields(audioItemPath))
    }

    @Transient
    override val coverImageBytes: ByteArray? = initialCoverImage
        get() = field ?: getCoverBytes(this)

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

    override fun update(change: AudioItemChange): AudioItem {
        return ImmutableAudioItem(
            id,
            path,
            change.title ?: title,
            duration,
            bitRate,
            change.artist ?: artist,
            ImmutableAlbum(
                change.albumName ?: album.name,
                change.albumArtist ?: album.albumArtist,
                change.isCompilation ?: album.isCompilation,
                change.year?.takeIf { year -> year > 0 } ?: album.year,
                change.label ?: album.label
            ),
            change.genre ?: genre,
            change.comments ?: comments,
            change.trackNumber?.takeIf { trackNum -> trackNum > 0 } ?: trackNumber,
            change.discNumber?.takeIf { discNum -> discNum > 0 } ?: discNumber,
            change.bpm?.takeIf { bpm -> bpm > 0 } ?: bpm,
            encoder,
            encoding,
            change.coverImageBytes ?: coverImageBytes,
            dateOfCreation,
            LocalDateTime.now())
    }

    override fun update(changeAction: AudioItemChange.() -> Unit): AudioItem =
        AudioItemChange(id).let { change ->
            change.changeAction()
            update(change)
        }

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

    override fun toString() = "ImmutableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}

fun readAudioItemFields(audioItemPath: Path): AudioItemAttributes {
    require(Files.exists(audioItemPath)) { "File '${audioItemPath.toAbsolutePath()}' does not exist" }

    val audioFile = AudioFileIO.read(audioItemPath.toFile())
    val audioHeader = audioFile.audioHeader
    val encoding = audioHeader.encodingType
    val duration = Duration.ofSeconds(audioHeader.trackLength.toLong())
    val bitRate = getBitRate(audioHeader)
    val extension = audioItemPath.extension
    val tag: Tag = audioFile.tag

    val title = getFieldIfExisting(tag, FieldKey.TITLE) ?: ""
    val artist = readArtist(tag)
    val album = readAlbum(tag, extension)
    val genre = getFieldIfExisting(tag, FieldKey.GENRE)?.let { Genre.parseGenre(it) } ?: Genre.UNDEFINED
    val comments = getFieldIfExisting(tag, FieldKey.COMMENT)
    val trackNumber = getFieldIfExisting(tag, FieldKey.TRACK)?.takeIf { it.isNotEmpty().and(it != "0") }?.toShortOrNull()?.takeIf { it > 0 }
    val discNumber = getFieldIfExisting(tag, FieldKey.DISC_NO)?.takeIf { it.isNotEmpty().and(it != "0") }?.toShortOrNull()?.takeIf { it > 0 }
    val bpm = getFieldIfExisting(tag, FieldKey.BPM)?.takeIf { it.isNotEmpty().and(it != "0") }?.toFloatOrNull()?.takeIf { it > 0 }
    val encoder = getFieldIfExisting(tag, FieldKey.ENCODER)
    val coverImageBytes = getCoverBytes(tag)

    val now = LocalDateTime.now()
    return AudioItemAttributes(
        audioItemPath,
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
        coverImageBytes,
        now,
        now)
}

fun getCoverBytes(audioItem: AudioItem): ByteArray? =
    audioItem.path.toFile().let {
        if (it.exists() && it.canRead())
            getCoverBytes(AudioFileIO.read(it).tag)
        else null
    }

private fun getCoverBytes(tag: Tag): ByteArray? = tag.artworkList.isNotEmpty().takeIf { it }?.let { tag.firstArtwork.binaryData }

private fun getFieldIfExisting(tag: Tag, fieldKey: FieldKey): String? = tag.hasField(fieldKey).takeIf { it }.run { tag.getFirst(fieldKey) }

internal fun getBitRate(audioHeader: AudioHeader): Int {
    val bitRate = audioHeader.bitRate
    return if ("~" == bitRate.substring(0, 1)) {
        bitRate.substring(1).toInt()
    } else {
        bitRate.toInt()
    }
}

private fun readArtist(tag: Tag): Artist =
    getFieldIfExisting(tag, FieldKey.ARTIST)?.let { artistName ->
        val country = getFieldIfExisting(tag, FieldKey.COUNTRY)?.let { _country ->
            if (_country.isNotEmpty())
                CountryCode.valueOf(_country)
            else CountryCode.UNDEFINED
        } ?: CountryCode.UNDEFINED
        ImmutableArtist(AudioUtils.beautifyArtistName(artistName), country)
    } ?: ImmutableArtist.UNKNOWN

private fun readAlbum(tag: Tag, extension: String): Album =
    getFieldIfExisting(tag, FieldKey.ALBUM).let { albumName ->
        return if (albumName == null) {
            ImmutableAlbum.UNKNOWN
        } else {
            val albumArtistName = getFieldIfExisting(tag, FieldKey.ALBUM_ARTIST) ?: ""
            val isCompilation = getFieldIfExisting(tag, FieldKey.IS_COMPILATION)?.let {
                if ("m4a" == extension) "1" == tag.getFirst(FieldKey.IS_COMPILATION)
                else "true" == tag.getFirst(FieldKey.IS_COMPILATION)
            } ?: false
            val year = getFieldIfExisting(tag, FieldKey.YEAR)?.toShortOrNull()?.takeIf { it > 0 }
            val label = getFieldIfExisting(tag, FieldKey.GROUPING)?.let { ImmutableLabel(it) } as Label
            ImmutableAlbum(albumName, ImmutableArtist(AudioUtils.beautifyArtistName(albumArtistName)), isCompilation, year, label)
        }
    }
