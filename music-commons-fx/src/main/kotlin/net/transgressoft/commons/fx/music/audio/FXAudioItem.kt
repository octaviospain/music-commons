package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.ReactiveEntityBase
import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemManipulationException
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.ImmutableLabel
import net.transgressoft.commons.music.audio.Label
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import com.neovisionaries.i18n.CountryCode
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleFloatProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleSetProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
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
import java.util.Optional
import kotlin.io.path.extension
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = ObservableAudioItemSerializer::class)
class FXAudioItem internal constructor(
    override val path: Path,
    override val id: Int = UNASSIGNED_ID
):
    ObservableAudioItem, Comparable<ObservableAudioItem>, ReactiveEntityBase<Int, ObservableAudioItem>() {

        @Transient
        private val logger = KotlinLogging.logger {}

        private val ioScope =
            CoroutineScope(
                Dispatchers.IO + SupervisorJob() +
                    CoroutineExceptionHandler { _, exception ->
                        val errorText = "Error writing metadata of $this"
                        logger.error(errorText, exception)
                    }
            )

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
            lastDateModified: LocalDateTime,
            playCount: Short
        ): this(path, id) {
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
            this.lastDateModified = lastDateModified
            _playCountProperty.set(playCount.toInt())
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
        override val dateOfCreationProperty: ReadOnlyObjectProperty<LocalDateTime> = SimpleObjectProperty(this, "date of creation", _dateOfCreation)

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

        override var title: String = getFieldIfExisting(tag, FieldKey.TITLE) ?: ""
            set(value) {
                setAndNotify(value, field) {
                    field = value
                    artistsInvolvedProperty.clear()
                    artistsInvolvedProperty.addAll(
                        AudioUtils.getArtistsNamesInvolved(
                            value, artistProperty.value.name, albumProperty.value.albumArtist.name
                        )
                    )
                }
            }

        @Transient
        override val titleProperty: StringProperty =
            SimpleStringProperty(this, "title", title).apply {
                addListener { _, _, newTitle ->
                    title = newTitle
                }
            }

        override var artist: Artist = readArtist(tag)
            set(value) {
                setAndNotify(value, field) {
                    field = value
                    artistsInvolvedProperty.addAll(
                        AudioUtils.getArtistsNamesInvolved(
                            titleProperty.value, value.name, albumProperty.value.albumArtist.name
                        )
                    )
                }
            }

        @Transient
        override val artistProperty: ObjectProperty<Artist> =
            SimpleObjectProperty(this, "artist", artist).apply {
                addListener { _, _, newArtist ->
                    artist = newArtist
                }
            }

        override var album: Album = readAlbum(tag, extension)
            set(value) {
                setAndNotify(value, field) {
                    field = value
                    artistsInvolvedProperty.clear()
                    artistsInvolvedProperty.addAll(
                        AudioUtils.getArtistsNamesInvolved(
                            titleProperty.value, artistProperty.value.name, value.albumArtist.name
                        )
                    )
                }
            }

        @Transient
        override val albumProperty: ObjectProperty<Album> =
            SimpleObjectProperty(this, "album", album).apply {
                addListener { _, _, newAlbum ->
                    album = newAlbum
                }
            }

        override var genre: Genre = getFieldIfExisting(tag, FieldKey.GENRE)?.let { Genre.parseGenre(it) } ?: Genre.UNDEFINED
            set(value) {
                setAndNotify(value, field) {
                    field = value
                }
            }

        @Transient
        override val genreNameProperty =
            SimpleStringProperty(this, "genre", genre.name).apply {
                addListener { _, _, newGenreName ->
                    genre = Genre.parseGenre(newGenreName)
                }
            }

        override var comments: String? = getFieldIfExisting(tag, FieldKey.COMMENT)
            set(value) {
                setAndNotify(value, field) {
                    field = value
                }
            }

        @Transient
        override val commentsProperty =
            SimpleStringProperty(this, "comments", comments ?: "").apply {
                addListener { _, _, newComments ->
                    comments = newComments
                }
            }

        override var trackNumber: Short? = getFieldIfExisting(tag, FieldKey.TRACK)?.takeUnless { it.isEmpty().and(it == "0") }?.toShortOrNull()
            set(value) {
                setAndNotify(value, field) {
                    field = value
                }
            }

        @Transient
        override val trackNumberProperty =
            SimpleIntegerProperty(this, "track number", trackNumber?.toInt() ?: -1).apply {
                addListener { _, _, newTrackNumber ->
                    trackNumber = newTrackNumber.toShort()
                }
            }

        override var discNumber: Short? = getFieldIfExisting(tag, FieldKey.DISC_NO)?.takeUnless { it.isEmpty().and(it == "0") }?.toShortOrNull()
            set(value) {
                setAndNotify(value, field) {
                    field = value
                }
            }

        @Transient
        override val discNumberProperty =
            SimpleIntegerProperty(this, "disc number", discNumber?.toInt() ?: -1).apply {
                addListener { _, _, newDiscNumber ->
                    discNumber = newDiscNumber.toShort()
                }
            }

        override var bpm: Float? = getFieldIfExisting(tag, FieldKey.BPM)?.takeUnless { it.isEmpty().and(it == "0") }?.toFloatOrNull() ?: -1f
            set(value) {
                setAndNotify(value, field) {
                    field = value
                }
            }

        @Transient
        override val bpmProperty =
            SimpleFloatProperty(this, "bpm", bpm ?: -1f).apply {
                addListener { _, _, newBpm ->
                    bpm = newBpm.takeUnless { it == -1f }?.toFloat()
                }
            }

        @Serializable
        override var lastDateModified: LocalDateTime = dateOfCreation
            set(value) {
                field = value
                _lastDateModifiedProperty.set(value)
            }

        private val _lastDateModifiedProperty = SimpleObjectProperty(this, "date of last modification", lastDateModified)

        @Transient
        override val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime> = _lastDateModifiedProperty

        override var coverImageBytes: ByteArray? = getCoverBytes(tag)
            set(value) {
                setAndNotify(value, field) {
                    field = value
                    _coverImageProperty.set(Optional.ofNullable(value).map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) })
                }
            }

        private val _coverImageProperty =
            SimpleObjectProperty(this, "cover image", Optional.ofNullable(coverImageBytes).map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) })

        @Transient
        override val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>> =
            _coverImageProperty.apply {
                addListener { _, oldValue, newValue ->
                    setAndNotify(newValue.orElse(null)?.url, oldValue.orElse(null)?.url) {}
                }
            }

        @Transient
        override val artistsInvolvedProperty: ReadOnlySetProperty<String> =
            SimpleSetProperty(
                this, "artists involved",
                FXCollections.observableSet(
                    AudioUtils.getArtistsNamesInvolved(
                        titleProperty.value, artistProperty.value.name, albumProperty.value.albumArtist.name
                    )
                )
            )

        override val artistsInvolved: Set<String>
            get() = artistsInvolvedProperty.value

        @Serializable
        override val playCount: Short
            get() = _playCountProperty.get().toShort()

        private val _playCountProperty = SimpleIntegerProperty(this, "play count", 0)

        @Transient
        override val playCountProperty: ReadOnlyIntegerProperty = _playCountProperty

        private fun getFieldIfExisting(tag: Tag, fieldKey: FieldKey): String? = tag.hasField(fieldKey).takeIf { it }.run { tag.getFirst(fieldKey) }

        private fun readArtist(tag: Tag): Artist =
            getFieldIfExisting(tag, FieldKey.ARTIST)?.let { artistName ->
                val country =
                    getFieldIfExisting(tag, FieldKey.COUNTRY)?.let { _country ->
                        if (_country.isNotEmpty()) CountryCode.valueOf(_country)
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
                    val isCompilation =
                        getFieldIfExisting(tag, FieldKey.IS_COMPILATION)?.let {
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

        override fun writeMetadata(): Job =
            ioScope.launch {
                logger.debug { "Writing metadata of $this to file '${path.toAbsolutePath()}'" }

                val audioFile = path.toFile()
                val audio = AudioFileIO.read(audioFile)
                createTag(audio.audioHeader.format).let {
                    audio.tag = it
                }

                audio.commit()
                logger.debug { "Metadata of $this successfully written to file" }
            }

        private fun createTag(format: String): Tag =
            when {
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

        internal fun incrementPlayCount() {
            setAndNotify(_playCountProperty.get() + 1, _playCountProperty.get()) {
                _playCountProperty.set(_playCountProperty.get() + 1)
            }
        }

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

        override fun clone(): FXAudioItem =
            FXAudioItem(
                path,
                id,
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
                lastDateModified,
                playCount
            )

        override fun toString() = "ObservableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
    }

val observableAudioItemSerializerModule =
    SerializersModule {
        polymorphic(ObservableAudioItem::class, ObservableAudioItemSerializer)
    }