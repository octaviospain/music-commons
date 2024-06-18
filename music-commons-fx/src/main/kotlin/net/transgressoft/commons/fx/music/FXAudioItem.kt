package net.transgressoft.commons.fx.music

import net.transgressoft.commons.ReactiveEntityBase
import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.audio.*
import com.neovisionaries.i18n.CountryCode
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.scene.image.Image
import mu.KotlinLogging
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavInfoTag
import org.jaudiotagger.tag.wav.WavTag
import org.jetbrains.kotlin.com.google.common.base.Objects
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.stream.Stream
import kotlin.io.path.extension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable(with = ObservableAudioItemSerializer::class)
class FXAudioItem internal constructor(
    override val path: Path,
    override val id: Int = UNASSIGNED_ID) :
    ObservableAudioItem, Comparable<ObservableAudioItem>, ReactiveEntityBase<Int, ObservableAudioItem>() {

    @Transient
    private val logger = KotlinLogging.logger {}

    internal constructor(
        path: Path,
        id: Int,
        title: String,
        duration: Duration,
        bitRate: Int,
        artist: Artist,
        album: Album,
        genre: Genre,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        encoding: String?,
        dateOfCreation: LocalDateTime,
        lastDateModified: LocalDateTime
    ) : this(path, id) {
        this.title = title
        this._duration = duration
        this.artist = artist
        this.genre = genre
        this.comments = comments
        this.trackNumber = trackNumber
        this.discNumber = discNumber
        this.bpm = bpm
        this.album = album
        this._bitRate = bitRate
        this._encoder = encoder
        this._encoding = encoding
        this._dateOfCreation = dateOfCreation
        this._lastDateModified = lastDateModified
    }

    @Transient
    private val audioFile = AudioFileIO.read(path.toFile())

    @Transient
    private val audioHeader = audioFile.audioHeader

    @Transient
    private val tag: Tag = audioFile.tag

    init {
        require(Files.exists(path)) { "File '${path.toAbsolutePath()}' does not exist" }
    }

    /** Immutable properties */

    private var _bitRate: Int = getBitRate(audioHeader)

    @Serializable
    override val bitRate: Int = _bitRate

    private var _duration: Duration = Duration.ofSeconds(audioHeader.trackLength.toLong())

    override val duration: Duration = _duration

    private var _encoder: String? = getFieldIfExisting(tag, FieldKey.ENCODER) ?: ""

    @Serializable
    override val encoder: String? = _encoder

    private var _encoding: String? = audioHeader.encodingType

    @Serializable
    override val encoding: String? = _encoding

    private var _dateOfCreation: LocalDateTime = LocalDateTime.now()

    @Serializable
    override val dateOfCreation: LocalDateTime
        get() = _dateOfCreation

    @Transient
    val dateOfCreationProperty: ReadOnlyObjectProperty<LocalDateTime> = SimpleObjectProperty(this, "date of creation", _dateOfCreation)

    override val fileName by lazy {
        path.fileName.toString()
    }

    override val extension by lazy {
        path.extension
    }

    override val length by lazy {
        path.toFile().length()
    }

    override val uniqueId
        get() =
            buildString {
                append(path.fileName.toString().replace(' ', '_'))
                append("-$title")
                append("-${duration.toSeconds()}")
                append("-$bitRate")
            }

    /** Mutable properties */

    @Transient
    val titleProperty: StringProperty = SimpleStringProperty(this, "title", getFieldIfExisting(tag, FieldKey.TITLE) ?: "").apply {
        addListener { _, _, newValue ->
            artistsInvolvedProperty.clear()
            artistsInvolvedProperty.addAll(
                AudioUtils.getArtistsNamesInvolved(
                    newValue,
                    artistProperty.value.name,
                    albumProperty.value.albumArtist.name
                )
            )
        }
    }

    override var title: String
        get() = titleProperty.get()
        set(value) { titleProperty.set(value) }

    @Transient
    val artistProperty: ObjectProperty<Artist> = SimpleObjectProperty(this, "artist", readArtist(tag)).apply {
        addListener { _, _, newValue ->
            artistsInvolvedProperty.addAll(
                AudioUtils.getArtistsNamesInvolved(
                    titleProperty.value,
                    newValue.name,
                    albumProperty.value.albumArtist.name
                )
            )
        }
    }

    override var artist: Artist
        get() = artistProperty.get()
        set(value) { artistProperty.set(value) }

    @Transient
    val albumProperty = SimpleObjectProperty(this, "album", readAlbum(tag, extension)).apply {
        addListener { _, _, newValue ->
            artistsInvolvedProperty.clear()
            artistsInvolvedProperty.addAll(
                AudioUtils.getArtistsNamesInvolved(
                    titleProperty.value,
                    artistProperty.value.name,
                    newValue.albumArtist.name
                )
            )
        }
    }

    override var album: Album
        get() = albumProperty.get()
        set(value) { albumProperty.set(value) }

    @Transient
    val genreNameProperty = SimpleStringProperty(this, "genre", getFieldIfExisting(tag, FieldKey.GENRE)?.let { Genre.parseGenre(it).name } ?: Genre.UNDEFINED.name)

    override var genre: Genre
        get() = Genre.parseGenre(genreNameProperty.get())
        set(value) { genreNameProperty.set(value.name) }

    @Transient
    val commentsProperty = SimpleStringProperty(this, "comments", getFieldIfExisting(tag, FieldKey.COMMENT) ?: "")

    override var comments: String?
        get() = commentsProperty.get()
        set(value) { commentsProperty.set(value) }

    @Transient
    val trackNumberProperty = SimpleIntegerProperty(this, "track number",  getFieldIfExisting(tag, FieldKey.TRACK)?.takeIf { it.isNotEmpty().and(it != "0") }?.toIntOrNull() ?: -1)

    override var trackNumber: Short?
        get() = trackNumberProperty.value.toShort()
        set(value) { trackNumberProperty.set(value?.toInt() ?: -1) }

    @Transient
    val discNumberProperty = SimpleIntegerProperty(this, "disc number", getFieldIfExisting(tag, FieldKey.DISC_NO)?.takeIf { it.isNotEmpty().and(it != "0") }?.toIntOrNull() ?: -1)

    override var discNumber: Short?
        get() = discNumberProperty.value.toShort()
        set(value) { discNumberProperty.set(value?.toInt() ?: -1) }

    @Transient
    val bpmProperty = SimpleFloatProperty(this, "bpm", getFieldIfExisting(tag, FieldKey.BPM)?.takeIf { it.isNotEmpty().and(it != "0") }?.toFloatOrNull() ?: -1f)

    override var bpm: Float?
        get() = bpmProperty.value
        set(value) { bpmProperty.set(value ?: -1f) }

    private var _lastDateModified = dateOfCreation

    override val lastDateModified: LocalDateTime
        get() = _lastDateModified

    @Transient
    val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime> = SimpleObjectProperty(this, "date of creation", _lastDateModified)

    override var coverImageBytes: ByteArray? = getCoverBytes(tag)
        set(value) {
            field = value
            coverImageProperty.set(Optional.ofNullable(value).map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) })
        }

    @Transient
    val coverImageProperty = SimpleObjectProperty(this, "cover image", Optional.ofNullable(coverImageBytes).map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) })

    @Transient
    val artistsInvolvedProperty: ReadOnlySetProperty<String> =
        SimpleSetProperty(this, "artists involved", FXCollections.observableSet(AudioUtils.getArtistsNamesInvolved(
            titleProperty.value,
            artistProperty.value.name,
            albumProperty.value.albumArtist.name)))

    override val artistsInvolved: Set<String>
        get() = artistsInvolvedProperty.value

    private fun getFieldIfExisting(tag: Tag, fieldKey: FieldKey): String? = tag.hasField(fieldKey).takeIf { it }.run { tag.getFirst(fieldKey) }

    private fun readArtist(tag: Tag): Artist =
        getFieldIfExisting(tag, FieldKey.ARTIST)?.let { artistName ->
            val country = getFieldIfExisting(tag, FieldKey.COUNTRY)?.let { _country ->
                if (_country.isNotEmpty())
                    CountryCode.valueOf(_country)
                else CountryCode.UNDEFINED
            } ?: CountryCode.UNDEFINED
            ImmutableArtist.of(AudioUtils.beautifyArtistName(artistName), country)
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
                ImmutableAlbum(albumName, ImmutableArtist.of(AudioUtils.beautifyArtistName(albumArtistName)), isCompilation, year, label)
            }
        }

    private fun getBitRate(audioHeader: AudioHeader): Int {
        val bitRate = audioHeader.bitRate
        return if ("~" == bitRate.substring(0, 1)) {
            bitRate.substring(1).toInt()
        } else {
            bitRate.toInt()
        }
    }

    private fun getCoverBytes(tag: Tag): ByteArray? = tag.artworkList.isNotEmpty().takeIf { it }?.let { tag.firstArtwork.binaryData }

    override fun writeMetadata() {
        GlobalScope.launch(Dispatchers.IO) {
            logger.debug { "Writing metadata of $this to file '${path.toAbsolutePath()}'" }

            val audioFile = path.toFile()
            try {
                val audio = AudioFileIO.read(audioFile)
                createTagTag(audio.audioHeader.format).let {
                    audio.tag = it
                }

                audio.commit()
                logger.debug { "Metadata of $this successfully written to file" }
            } catch (exception: Exception) {
                val errorText = "Error writing metadata of $this"
                logger.error(errorText, exception)
                throw AudioItemManipulationException(errorText, exception)
            }
        }
    }

    private fun createTagTag(format: String): Tag {
        return when {
            format.startsWith("Wav", ignoreCase = true) -> {
                val wavTag = WavTag(WavOptions.READ_ID3_ONLY)
                wavTag.iD3Tag = ID3v24Tag()
                wavTag.infoTag = WavInfoTag()
                wavTag
            }

            format.startsWith("Mp3", ignoreCase = true) -> {
                TagOptionSingleton.getInstance().isWriteMp3GenresAsText = true
                val tag: Tag = ID3v24Tag()
                tag.artworkList.clear()
                tag
            }

            format.startsWith("Flac", ignoreCase = true) -> {
                val tag: Tag = FlacTag()
                tag.artworkList.clear()
                tag
            }

            format.startsWith("Aac", ignoreCase = true) -> {
                TagOptionSingleton.getInstance().isWriteMp4GenresAsText = true
                val tag: Tag = Mp4Tag()
                tag.artworkList.clear()
                tag
            }

            else -> WavInfoTag()
        }.also {
            setTrackFieldsToTag(it)
        }
    }

    private fun setTrackFieldsToTag(tag: Tag) {
        tag.setField(FieldKey.TITLE, title)
        tag.setField(FieldKey.ALBUM, album.name)
        tag.setField(FieldKey.ALBUM_ARTIST, album.albumArtist.name)
        tag.setField(FieldKey.ARTIST, artist.name)
        tag.setField(FieldKey.GENRE, genre.capitalize())
        tag.setField(FieldKey.COUNTRY, artist.countryCode.name)
        comments?.let { tag.setField(FieldKey.COMMENT, it) }
        trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
        album.year?.let { tag.setField(FieldKey.YEAR, it.toString()) }
        tag.setField(FieldKey.ENCODER, encoder)
        tag.setField(FieldKey.GROUPING, album.label.name)
        discNumber?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }
        tag.setField(FieldKey.IS_COMPILATION, album.isCompilation.toString())
        bpm?.let {
            if (tag is Mp4Tag) {
                tag.setField(FieldKey.BPM, it.toInt().toString())
            } else {
                tag.setField(FieldKey.BPM, it.toString())
            }
        }
        coverImageBytes?.let {
            tag.deleteArtworkField()
            tag.addField(createArtwork(it))
        }
    }

    private fun createArtwork(coverBytes: ByteArray): Artwork {
        val tempCover: Path
        try {
            tempCover = Files.createTempFile("tempCover_$fileName", ".tmp")
            Files.write(tempCover, coverBytes, StandardOpenOption.CREATE)
            tempCover.toFile().deleteOnExit()
            return ArtworkFactory.createArtworkFromFile(tempCover.toFile())
        } catch (exception: IOException) {
            val errorText = "Error creating artwork of $this"
            logger.error(errorText, exception)
            throw AudioItemManipulationException(errorText, exception)
        }
    }

    fun isPlayable() = Stream.of("mp3", "m4a", "wav").anyMatch { fileFormat: String? -> extension.equals(fileFormat, ignoreCase = true) }

    override operator fun compareTo(other: ObservableAudioItem) = AudioUtils.audioItemTrackDiscNumberComparator<ObservableAudioItem>().compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as FXAudioItem
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

    override fun toString() = "ObservableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
}
