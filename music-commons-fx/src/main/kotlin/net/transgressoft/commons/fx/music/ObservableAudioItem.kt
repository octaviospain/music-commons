package net.transgressoft.commons.fx.music

import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.audio.*
import com.google.common.base.Objects
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.scene.image.Image
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*
import java.util.stream.Stream
import kotlin.properties.Delegates.observable
import kotlinx.serialization.Transient

class ObservableAudioItem internal constructor(id: Int, attributes: AudioItemAttributes) : MutableAudioItem,
    MutableAudioItemBase(id, attributes) {

    companion object {
        fun createFromFile(audioItemPath: Path): ObservableAudioItem = ObservableAudioItem(UNASSIGNED_ID, readAudioItemFields(audioItemPath))
    }

    @Transient
    private val titleProperty = SimpleStringProperty(this, "title", attributes.title)

    @Transient
    private val artistNameProperty = SimpleStringProperty(this, "artist", attributes.artist.name)

    @Transient
    private val albumNameProperty = SimpleStringProperty(this, "album", attributes.album.name)

    @Transient
    private val albumYearProperty = SimpleIntegerProperty(this, "album year", attributes.album.year?.toInt() ?: -1)

    @Transient
    private val albumArtistNameProperty = SimpleStringProperty(this, "album artist", attributes.album.albumArtist.name)

    @Transient
    private val genreNameProperty = SimpleStringProperty(this, "genre", attributes.genre.name)

    @Transient
    private val labelNameProperty = SimpleStringProperty(this, "genre", attributes.album.label.name)

    @Transient
    private val commentsProperty = SimpleStringProperty(this, "comments", attributes.comments ?: "")

    @Transient
    private val trackNumberProperty = SimpleIntegerProperty(this, "track number", attributes.trackNumber?.toInt() ?: -1)

    @Transient
    private val discNumberProperty = SimpleIntegerProperty(this, "disc number", attributes.discNumber?.toInt() ?: -1)

    @Transient
    private val bpmProperty = SimpleFloatProperty(this, "bpm", attributes.bpm ?: -1f)

    @Transient
    private val lastDateModifiedProperty = SimpleObjectProperty(this, "date modified", attributes.lastDateModified)

    @Transient
    private val artistsInvolvedProperty = SimpleSetProperty<String>(this, "artists involved", FXCollections.observableSet())

    @Transient
    private val playCountProperty = SimpleIntegerProperty(this, "play count", 0)

    init {
        artistsInvolvedProperty.set(
            FXCollections.observableSet(
                AudioUtils.getArtistsNamesInvolved(
                    attributes.title,
                    attributes.artist.name,
                    attributes.album.albumArtist.name
                )
            )
        )
    }

    override var title: String by observable(attributes.title) { _, _, newValue ->
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

    override var artist: Artist by observable(attributes.artist) { _, _, newValue ->
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

    override var album: Album by observable(attributes.album) { _, _, newValue ->
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

    override var genre: Genre by observable(attributes.genre) { _, _, newValue ->
        newValue.takeIf { it != Genre.UNDEFINED }?.let {
            genreNameProperty.set(it.name)
        } ?: genreNameProperty.set("")
    }

    override var comments: String? by observable(attributes.comments) { _, _, newValue ->
        newValue?.let {
            commentsProperty.set(it)
        } ?: commentsProperty.set("")
    }

    override var trackNumber: Short? by observable(attributes.trackNumber) { _, _, newValue -> newValue?.let { trackNumberProperty.set(it.toInt()) } }

    override var discNumber: Short? by observable(attributes.discNumber) { _, _, newValue -> newValue?.let { discNumberProperty.set(it.toInt()) } }

    override var bpm: Float? by observable(attributes.bpm) { _, _, newValue -> newValue?.let { bpmProperty.set(it) } ?: -1f }

    override var coverImageBytes: ByteArray? = attributes.coverImageBytes

    override var lastDateModified: LocalDateTime = attributes.lastDateModified
        get() = lastDateModifiedProperty.get()
        set(value) {
            lastDateModifiedProperty.set(value)
            field = value
        }

    fun isPlayable() = Stream.of("mp3", "m4a", "wav").anyMatch { fileFormat: String? -> extension.equals(fileFormat, ignoreCase = true) }

    fun albumImage(): Optional<Image> = Optional.ofNullable(coverImageBytes).map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) }

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

    override fun update(change: AudioItemChange): ObservableAudioItem {
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
        bpm = change.bpm?.takeIf { bpm -> bpm > 0 } ?: bpm
        coverImageBytes = change.coverImageBytes ?: coverImageBytes
        lastDateModified = LocalDateTime.now()
        return this
    }

    override fun update(changeAction: AudioItemChange.() -> Unit): ObservableAudioItem =
        AudioItemChange(id).let { change ->
            change.changeAction()
            update(change)
        }

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
