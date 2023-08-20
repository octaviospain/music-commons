package net.transgressoft.commons.music.audio

import com.google.common.base.Objects
import com.neovisionaries.i18n.CountryCode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.audio.AudioItemBase.AudioItemBaseBuilder
import net.transgressoft.commons.query.QueryEntity
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.nio.file.Files
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
    @Transient override val id: Int = UNASSIGNED_ID,
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
    @Transient private val _coverImage: ByteArray? = null,
    @Transient override val dateOfCreation: LocalDateTime = LocalDateTime.now(),
    @Transient override val lastDateModified: LocalDateTime = dateOfCreation
) : AudioItem, Comparable<AudioItem> {

    override val coverImage: ByteArray?
        get() = _coverImage ?: getCoverBytes(this)

    override val uniqueId by lazy {
        StringJoiner("-")
            .add(path.fileName.toString().replace(' ', '_'))
            .add(title)
            .add(duration.toSeconds().toString())
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
        AudioUtils.getArtistsNamesInvolved(title, artist.name, album.albumArtist.name)
    }

    override val length by lazy {
        path.toFile().length()
    }

    override fun toBuilder() = AudioItemBaseBuilder()
        .id(id)
        .path(path)
        .title(title)
        .duration(duration)
        .bitRate(bitRate)
        .artist(artist)
        .album(album)
        .genre(genre)
        .comments(comments)
        .trackNumber(trackNumber)
        .discNumber(discNumber)
        .bpm(bpm)
        .encoder(encoder)
        .encoding(encoding)
        .coverImage(coverImage)
        .dateOfCreation(dateOfCreation)
        .lastDateModified(lastDateModified)

    override suspend fun writeMetadata() = JAudioTaggerMetadataWriter().writeMetadata(this)

    override operator fun compareTo(other: AudioItem) =
        Comparator.comparing(QueryEntity::uniqueId, java.lang.String.CASE_INSENSITIVE_ORDER).compare(this, other)

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

    open class AudioItemBaseBuilder(builder: AudioItemBuilder<out AudioItem>?) : AudioItemBuilder<AudioItem> {

        constructor() : this(null)

        override var id: Int = UNASSIGNED_ID
        override var path: Path = Path.of("")
        override var title: String = ""
        override var duration: Duration = Duration.ZERO
        override var bitRate: Int = 0
        override var artist: Artist = ImmutableArtist.UNKNOWN
        override var album: Album = ImmutableAlbum.UNKNOWN
        override var genre: Genre = Genre.UNDEFINED
        override var comments: String? = null
        override var trackNumber: Short? = null
        override var discNumber: Short? = null
        override var bpm: Float? = null
        override var encoder: String? = null
        override var encoding: String? = null
        override var coverImage: ByteArray? = null
        override var dateOfCreation: LocalDateTime = LocalDateTime.now()
        override var lastDateModified: LocalDateTime = LocalDateTime.now()

        init {
            builder?.let {
                id = it.id
                path = it.path
                title = it.title
                duration = it.duration
                bitRate = it.bitRate
                artist = it.artist
                album = it.album
                genre = it.genre
                comments = it.comments
                trackNumber = it.trackNumber
                discNumber = it.discNumber
                bpm = it.bpm
                encoder = it.encoder
                encoding = it.encoding
                coverImage = it.coverImage
                dateOfCreation = it.dateOfCreation
                lastDateModified = it.lastDateModified
            }
        }

        override fun build(): AudioItemBase = ImmutableAudioItem(
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
            coverImage,
            dateOfCreation,
            lastDateModified
        )

        override fun id(id: Int) = apply { this.id = id }
        override fun path(path: Path) = apply { this.path = path }
        override fun title(title: String) = apply { this.title = title }
        override fun duration(duration: Duration) = apply { this.duration = duration }
        override fun bitRate(bitRate: Int) = apply { this.bitRate = bitRate }
        override fun artist(artist: Artist) = apply { this.artist = artist }
        override fun album(album: Album) = apply { this.album = album }
        override fun genre(genre: Genre) = apply { this.genre = genre }
        override fun comments(comments: String?) = apply { this.comments = comments }
        override fun trackNumber(trackNumber: Short?) = apply { this.trackNumber = trackNumber }
        override fun discNumber(discNumber: Short?) = apply { this.discNumber = discNumber }
        override fun bpm(bpm: Float?) = apply { this.bpm = bpm }
        override fun encoder(encoder: String?) = apply { this.encoder = encoder }
        override fun encoding(encoding: String?) = apply { this.encoding = encoding }
        override fun coverImage(coverImage: ByteArray?) = apply { this.coverImage = coverImage }
        override fun dateOfCreation(dateOfCreation: LocalDateTime) = apply { this.dateOfCreation = dateOfCreation }
        override fun lastDateModified(lastDateModified: LocalDateTime) = apply { this.lastDateModified = lastDateModified }
    }
}

fun readAudioItemFields(audioItemPath: Path): AudioItemBuilder<AudioItem> {
    require(!Files.notExists(audioItemPath)) { "File '${audioItemPath.toAbsolutePath()}' does not exist" }

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
    val coverBytes = getCoverBytes(tag)

    val now = LocalDateTime.now()
    return AudioItemBaseBuilder()
        .path(audioItemPath)
        .title(title)
        .duration(duration)
        .bitRate(bitRate)
        .artist(artist)
        .album(album)
        .genre(genre)
        .comments(comments)
        .trackNumber(trackNumber)
        .discNumber(discNumber)
        .bpm(bpm)
        .encoder(encoder)
        .encoding(encoding)
        .coverImage(coverBytes)
        .lastDateModified(now)
        .dateOfCreation(now)
}

private fun getCoverBytes(audioItem: AudioItem) = getCoverBytes(AudioFileIO.read(audioItem.path.toFile()).tag)

private fun getCoverBytes(tag: Tag): ByteArray? = tag.artworkList.isNotEmpty().takeIf { it }?.let { tag.firstArtwork.binaryData }

private fun getFieldIfExisting(tag: Tag, fieldKey: FieldKey): String? = tag.hasField(fieldKey).takeIf { it }.run { tag.getFirst(fieldKey) }

private fun getBitRate(audioHeader: AudioHeader): Int {
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
    with(getFieldIfExisting(tag, FieldKey.ALBUM)) {
        return if (this == null) {
            ImmutableAlbum.UNKNOWN
        } else {
            val albumArtistName = getFieldIfExisting(tag, FieldKey.ALBUM_ARTIST) ?: ""
            val isCompilation = getFieldIfExisting(tag, FieldKey.IS_COMPILATION)?.let {
                if ("m4a" == extension) "1" == tag.getFirst(FieldKey.IS_COMPILATION)
                else "true" == tag.getFirst(FieldKey.IS_COMPILATION)
            } ?: false
            val year = getFieldIfExisting(tag, FieldKey.YEAR)?.toShortOrNull()?.takeIf { it > 0 }
            val label = getFieldIfExisting(tag, FieldKey.GROUPING)?.let { ImmutableLabel(it) } as Label
            ImmutableAlbum(this, ImmutableArtist(AudioUtils.beautifyArtistName(albumArtistName)), isCompilation, year, label)
        }
    }
