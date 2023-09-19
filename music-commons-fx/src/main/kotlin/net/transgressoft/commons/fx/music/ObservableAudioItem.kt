package net.transgressoft.commons.fx.music

import com.google.common.base.Objects
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.scene.image.Image
import net.transgressoft.commons.music.audio.*
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.AudioUtils.readAudioItemFields
import java.io.ByteArrayInputStream
import java.util.stream.Stream
import java.util.Optional
import kotlin.properties.Delegates.observable

@Serializable
class ObservableAudioItem internal constructor(
    @Transient private val initialId: Int = UNASSIGNED_ID,
    @Transient private val initialPath: Path = Path.of(""),
    @Transient private val initialTitle: String = "",
    @Transient private val initialDuration: Duration = Duration.ZERO,
    @Transient private val initialBitrate: Int = 0,
    @Transient private val initialArtist: Artist = ImmutableArtist.UNKNOWN,
    @Transient private val initialAlbum: Album = ImmutableAlbum.UNKNOWN,
    @Transient private val initialGenre: Genre = Genre.UNDEFINED,
    @Transient private val initialComments: String? = null,
    @Transient private val initialTrackNumber: Short? = null,
    @Transient private val initialDiscNumber: Short? = null,
    @Transient private val initialBpm: Float? = null,
    @Transient private val initialEncoder: String? = null,
    @Transient private val initialEncoding: String? = null,
    @Transient private val initialCoverImage: ByteArray? = null,
    @Transient private val initialDateOfCreation: LocalDateTime = LocalDateTime.now(),
    @Transient private val initialLastDateModified: LocalDateTime = initialDateOfCreation
) : AudioItemBase(
    initialPath,
    initialTitle,
    initialDuration,
    initialBitrate,
    initialArtist,
    initialAlbum,
    initialGenre,
    initialComments,
    initialTrackNumber,
    initialDiscNumber,
    initialBpm,
    initialEncoder,
    initialEncoding,
    initialCoverImage,
    initialDateOfCreation,
    initialLastDateModified
) {

    companion object {
        fun createFromFile(audioItemPath: Path): ObservableAudioItem = ObservableAudioItemBuilder(readAudioItemFields(audioItemPath)).build()
    }

    @Transient
    private val titleProperty = SimpleStringProperty(this, "title", initialTitle)

    @Transient
    private val artistNameProperty = SimpleStringProperty(this, "artist", initialArtist.name)

    @Transient
    private val albumNameProperty = SimpleStringProperty(this, "album", initialAlbum.name)

    @Transient
    private val albumYearProperty = SimpleIntegerProperty(this, "album year", initialAlbum.year?.toInt() ?: -1)

    @Transient
    private val albumArtistNameProperty = SimpleStringProperty(this, "album artist", initialAlbum.albumArtist.name)

    @Transient
    private val genreNameProperty = SimpleStringProperty(this, "genre", initialGenre.name)

    @Transient
    private val labelNameProperty = SimpleStringProperty(this, "genre", initialAlbum.label.name)

    @Transient
    private val commentsProperty = SimpleStringProperty(this, "comments", initialComments)

    @Transient
    private val trackNumberProperty = SimpleIntegerProperty(this, "track number", initialTrackNumber?.toInt() ?: -1)

    @Transient
    private val discNumberProperty = SimpleIntegerProperty(this, "disc number", initialDiscNumber?.toInt() ?: -1)

    @Transient
    private val bpmProperty = SimpleFloatProperty(this, "bpm", initialBpm ?: -1f)

    @Transient
    private val lastDateModifiedProperty = SimpleObjectProperty(this, "date modified", initialLastDateModified)

    @Transient
    private val artistsInvolvedProperty = SimpleSetProperty<String>(this, "artists involved", FXCollections.observableSet())

    @Transient
    private val playCountProperty = SimpleIntegerProperty(this, "play count", 0)

    init {
        artistsInvolvedProperty.set(
            FXCollections.observableSet(
                AudioUtils.getArtistsNamesInvolved(
                    initialTitle,
                    initialArtist.name,
                    initialAlbum.albumArtist.name
                )
            )
        )
    }

    override var title: String by observable(initialTitle) { _, _, newValue ->
        titleProperty.set(newValue)
        artistsInvolvedProperty.clear()
        artistsInvolvedProperty.addAll(
            AudioUtils.getArtistsNamesInvolved(
                titleProperty.get(),
                artistNameProperty.get(),
                albumArtistNameProperty.get()
            )
        )
    }

    override var artist: Artist by observable(initialArtist) { _, _, newValue ->
        artistNameProperty.set(newValue.name)
        artistsInvolvedProperty.clear()
        artistsInvolvedProperty.addAll(
            AudioUtils.getArtistsNamesInvolved(
                titleProperty.get(),
                artistNameProperty.get(),
                albumArtistNameProperty.get()
            )
        )
    }

    override var album: Album by observable(initialAlbum) { _, _, newValue ->
        albumNameProperty.set(newValue.name)
        albumArtistNameProperty.set(newValue.albumArtist.name)
        newValue.year?.toInt()?.let { albumYearProperty.set(it) } ?: albumYearProperty.set(-1)
        newValue.label.takeIf { it != ImmutableLabel.UNKNOWN }?.let {
            labelNameProperty.set(it.name)
        } ?: labelNameProperty.set("")
        artistsInvolvedProperty.clear()
        artistsInvolvedProperty.addAll(
            AudioUtils.getArtistsNamesInvolved(
                titleProperty.get(),
                artistNameProperty.get(),
                albumArtistNameProperty.get()
            )
        )
    }

    override var genre: Genre by observable(initialGenre) { _, _, newValue ->
        newValue.takeIf { it != Genre.UNDEFINED }?.let {
            genreNameProperty.set(it.name)
        } ?: genreNameProperty.set("")
    }

    override var comments: String? by observable(initialComments) { _, _, newValue ->
        newValue?.let {
            commentsProperty.set(it)
        } ?: commentsProperty.set("")
    }

    override var trackNumber: Short? by observable(initialTrackNumber) { _, _, newValue -> newValue?.let { trackNumberProperty.set(it.toInt()) } }

    override var discNumber: Short? by observable(initialDiscNumber) { _, _, newValue -> newValue?.let { discNumberProperty.set(it.toInt()) } }

    override var bpm: Float? by observable(initialBpm) { _, _, newValue -> newValue?.let { bpmProperty.set(it) } ?: -1f }

    override val artistsInvolved: Set<String>
        get() = artistsInvolvedProperty

    override var coverImage: ByteArray? = initialCoverImage

    override var lastDateModified: LocalDateTime = initialLastDateModified
        get() = lastDateModifiedProperty.get()
        private set(value) {
            lastDateModifiedProperty.set(value)
            field = value
        }

    fun isPlayable() = Stream.of("mp3", "m4a", "wav").anyMatch { fileFormat: String? -> extension.equals(fileFormat, ignoreCase = true) }

    fun albumImage(): Optional<Image> = Optional.ofNullable(coverImage).map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) }

    fun titleProperty() = titleProperty

    fun artistNameProperty() = artistNameProperty

    fun albumNameProperty() = albumNameProperty

    fun albumYearProperty() = albumYearProperty

    fun albumArtistNameProperty() = albumArtistNameProperty

    fun labelNameProperty() = labelNameProperty

    fun genreNameProperty() = genreNameProperty

    fun commentsProperty() = commentsProperty

    fun trackNumberProperty() = trackNumberProperty

    fun discNumberProperty() = discNumberProperty

    fun bpmProperty() = bpmProperty

    fun lastDateModifiedProperty(): ReadOnlyObjectProperty<LocalDateTime> = lastDateModifiedProperty

    fun artistsInvolvedProperty(): ReadOnlySetProperty<String> = artistsInvolvedProperty

    fun playCountProperty(): ReadOnlyIntegerProperty = playCountProperty

    override fun update(change: AudioItemMetadataChange): ObservableAudioItem {
        title = change.title ?: title
        artist = change.artist ?: artist
        album = ImmutableAlbum(
            change.albumName ?: album.name,
            change.albumArtist ?: album.albumArtist,
            change.isCompilation ?: album.isCompilation,
            change.year?.takeIf { year -> year > 0 } ?: album.year,
            change.label ?: album.label
        )
        genre = change.genre ?: genre
        comments = change.comments ?: comments
        trackNumber = change.trackNumber?.takeIf { trackNum -> trackNum > 0 } ?: trackNumber
        discNumber = change.discNumber?.takeIf { discNum -> discNum > 0 } ?: discNumber
        bpm = change.bpm ?: bpm
        coverImage = change.coverImage ?: coverImage
        lastDateModified = LocalDateTime.now()
        return this
    }

    override fun update(changeAction: AudioItemMetadataChange.() -> Unit): ObservableAudioItem =
        AudioItemMetadataChange().let { change ->
            change.changeAction()
            update(change)
        }

    override fun toBuilder(): ObservableAudioItemBuilder = ObservableAudioItemBuilder(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ObservableAudioItem
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

    override fun toString() = "MusicottAudioItem(id=$id, path=$path, title=$title, artist=${artist.name}, album=${album.name})"
}

class ObservableAudioItemBuilder(audioItem: ObservableAudioItem?) : AudioItemBuilderBase<ObservableAudioItem>(audioItem) {

    constructor() : this(null)

    internal constructor(builder: AudioItemBuilder<AudioItem>) : this(null) {
        id = builder.id
        path = builder.path
        title = builder.title
        duration = builder.duration
        bitRate = builder.bitRate
        artist = builder.artist
        album = builder.album
        genre = builder.genre
        comments = builder.comments
        trackNumber = builder.trackNumber
        discNumber = builder.discNumber
        bpm = builder.bpm
        encoder = builder.encoder
        encoding = builder.encoding
        coverImage = builder.coverImage
        dateOfCreation = builder.dateOfCreation
        lastDateModified = builder.lastDateModified
    }

    override fun build(): ObservableAudioItem {
        return ObservableAudioItem(
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
    }
}