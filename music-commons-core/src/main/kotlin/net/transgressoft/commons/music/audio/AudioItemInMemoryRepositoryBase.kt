package net.transgressoft.commons.music.audio

import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import com.google.common.collect.ImmutableSet
import com.neovisionaries.i18n.CountryCode
import net.transgressoft.commons.event.QueryEventDispatcher
import net.transgressoft.commons.music.audio.AlbumAttribute.ALBUM
import net.transgressoft.commons.music.audio.ArtistAttribute.ARTIST
import net.transgressoft.commons.music.audio.ArtistsInvolvedAttribute.ARTISTS_INVOLVED
import net.transgressoft.commons.music.audio.AudioItemDurationAttribute.DURATION
import net.transgressoft.commons.music.audio.AudioItemFloatAttribute.BPM
import net.transgressoft.commons.music.audio.AudioItemIntegerAttribute.BITRATE
import net.transgressoft.commons.music.audio.AudioItemLocalDateTimeAttribute.DATE_OF_CREATION
import net.transgressoft.commons.music.audio.AudioItemPathAttribute.PATH
import net.transgressoft.commons.music.audio.AudioItemShortAttribute.DISC_NUMBER
import net.transgressoft.commons.music.audio.AudioItemShortAttribute.TRACK_NUMBER
import net.transgressoft.commons.music.audio.AudioItemStringAttribute.*
import net.transgressoft.commons.query.InMemoryRepository
import org.apache.commons.io.FilenameUtils
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagException
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * @author Octavio Calleya
 */
abstract class AudioItemInMemoryRepositoryBase<I : AudioItem> protected constructor(
    audioItems: MutableMap<Int, I>,
    private val eventDispatcher: QueryEventDispatcher<I>?,
) : InMemoryRepository<I>(audioItems, eventDispatcher), AudioItemRepository<I> {
    private val log = LoggerFactory.getLogger(AudioItemInMemoryRepositoryBase::class.java.name)
    private val idCounter = AtomicInteger(1)
    private val albumsByArtist: MutableMap<Artist?, MutableSet<Album>?>

    init {
        albumsByArtist = audioItems.values.stream().collect(
            Collectors.groupingBy({ it.artist() },
                Collectors.mapping({ it.album() },
                    Collectors.toSet())))
    }

    protected fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    override fun add(entity: I): Boolean {
        val added = super.add(entity)
        addOrReplaceAlbumByArtist(entity, added)
        return added
    }

    private fun addOrReplaceAlbumByArtist(audioItem: AudioItem, added: Boolean) {
        val artist = audioItem.artist()
        val album = audioItem.album()
        if (added) {
            if (albumsByArtist.containsKey(artist)) {
                val mappedAlbums = albumsByArtist[artist]
                if (!albumsByArtist[artist]!!.contains(album)) {
                    mappedAlbums!!.add(album)
                }
            } else {
                val newSet = HashSet<Album>()
                newSet.add(album)
                albumsByArtist[artist] = newSet
            }
        }
    }

    override fun addOrReplace(entity: I): Boolean {
        val addedOrReplaced = super.addOrReplace(entity)
        addOrReplaceAlbumByArtist(entity, addedOrReplaced)
        return addedOrReplaced
    }

    override fun addOrReplaceAll(entities: Set<I>): Boolean {
        val addedOrReplaced = super.getAddedOrReplacedEntities(entities)
        addedOrReplaced[true]?.let { addedList ->
            if (addedList.isNotEmpty()) {
                addedList.forEach(Consumer { addOrReplaceAlbumByArtist(it, true) })
                eventDispatcher?.putCreateEvent(addedList)
            }
        }
        addedOrReplaced[false]?.let { replacedList ->
            if (replacedList.isNotEmpty()) {
                replacedList.forEach(Consumer { addOrReplaceAlbumByArtist(it, true) })
                eventDispatcher?.putUpdateEvent(replacedList)
            }
        }
        return addedOrReplaced.values.stream().flatMap { it.stream() }.findAny().isPresent
    }

    override fun remove(entity: I): Boolean {
        val removed = super.remove(entity)
        removeAlbumByArtistInternal(entity)
        return removed
    }

    private fun removeAlbumByArtistInternal(audioItem: AudioItem) {
        val artist = audioItem.artist()
        if (albumsByArtist.containsKey(artist)) {
            var albums = albumsByArtist[audioItem.artist()]
            albums = albums?.stream()?.filter { album: Album -> album.audioItems().isNotEmpty() }?.collect(Collectors.toSet())
            if (albums != null) {
                if (albums.isEmpty()) {
                    albumsByArtist.remove(artist)
                } else {
                    albumsByArtist[artist] = albums
                }
            }
        }
    }

    override fun removeAll(entities: Set<I>): Boolean {
        val removed = super.removeAll(entities)
        entities.forEach { audioItem: AudioItem -> removeAlbumByArtistInternal(audioItem) }
        return removed
    }

    override fun containsAudioItemWithArtist(artistName: String): Boolean {
        return contains(ARTISTS_INVOLVED.containsElement(artistName))
    }

    override fun artistAlbums(artist: Artist): Set<Album> {
        return ImmutableSet.copyOf(albumsByArtist[artist] ?: emptySet())
    }

    inner class ImmutableAlbum(
        name: String,
        private val albumArtist: Artist,
        private val isCompilation: Boolean,
        private val year: Short,
        private val label: Label,
        private val coverBytes: ByteArray?
    ) : Album {

        private val name: String

        init {
            this.name = beautifyName(name)
        }

        private fun beautifyName(name: String): String {
            return name.replace("\\s+".toRegex(), " ")
        }

        override fun name(): String {
            return name
        }

        override fun albumArtist(): Artist {
            return albumArtist
        }

        override fun audioItems(): Set<AudioItem> {
            return HashSet<AudioItem>(search(ALBUM.equalsTo(this)))
        }

        override fun isCompilation(): Boolean {
            return isCompilation
        }

        override fun year(): Short {
            return year
        }

        override fun label(): Label {
            return label
        }

        override fun coverImage(): Optional<ByteArray> {
            return Optional.ofNullable(coverBytes)
        }

        override fun compareTo(other: Album): Int {
            val nameComparison = name.compareTo(other.name())
            val artistComparison = albumArtist.name().compareTo(other.albumArtist().name())
            val labelComparison = label.name().compareTo(other.label().name())
            val yearComparison = year - other.year()
            return if (nameComparison != 0) {
                nameComparison
            } else if (artistComparison != 0) {
                artistComparison
            } else if (labelComparison != 0) {
                labelComparison
            } else yearComparison
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val that: AudioItemInMemoryRepositoryBase<*>.ImmutableAlbum = other as AudioItemInMemoryRepositoryBase<*>.ImmutableAlbum
            return isCompilation == that.isCompilation && year == that.year &&
                    Objects.equal(name, that.name) &&
                    Objects.equal(albumArtist, that.albumArtist) &&
                    Objects.equal(label, that.label)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(name, albumArtist, isCompilation, year, label)
        }

        override fun toString(): String {
            return MoreObjects.toStringHelper(ImmutableAlbum::class.java)
                .add("name", name)
                .add("albumArtist", albumArtist)
                .add("isCompilation", isCompilation)
                .add("year", year.toInt())
                .add("label", label)
                .toString()
        }
    }

    protected inner class JAudioTaggerMetadataReader {

        private val errorMessage = "Error parsing file {}: "

        @Throws(AudioItemManipulationException::class)
        fun readAudioItem(audioItemPath: Path): AudioItemAttributes {
            log.debug("Parsing file {}", audioItemPath)

            val result : AudioItemAttributes = try {

                val attributes = SimpleAudioItemAttributes()

                val audioItemFile = audioItemPath.toFile()
                val extension = FilenameUtils.getExtension(audioItemFile.name)
                val audioFile = AudioFileIO.read(audioItemFile)
                val tag = audioFile.tag
                val title = if (tag.hasField(FieldKey.TITLE)) tag.getFirst(FieldKey.TITLE) else ""
                val audioHeader = audioFile.audioHeader
                val duration = Duration.ofSeconds(audioHeader.trackLength.toLong())
                val bitRate = getBitRate(audioHeader)

                readMetadata(attributes, tag)
                readAlbum(attributes, extension, tag)

                attributes[PATH] = audioItemPath
                attributes[TITLE] = title
                attributes[DURATION] = duration
                attributes[BITRATE] = bitRate
                attributes[DATE_OF_CREATION] = LocalDateTime.now()
                attributes[ENCODING] = audioHeader.encodingType
                attributes
            } catch (exception: Exception) {
                when (exception) {
                    is CannotReadException, is ReadOnlyFileException, is TagException, is InvalidAudioFrameException -> {
                        log.debug(errorMessage, audioItemPath, exception)
                        throw AudioItemManipulationException("Error parsing file $audioItemPath", exception)
                    }
                    else -> {
                        throw exception
                    }
                }
            }

            return result
        }

        private fun getBitRate(audioHeader: AudioHeader): Int {
            val bitRate = audioHeader.bitRate
            return if ("~" == bitRate.substring(0, 1)) {
                bitRate.substring(1).toInt()
            } else {
                bitRate.toInt()
            }
        }

        private fun readMetadata(attributes: AudioItemAttributes, tag: Tag) {
            if (tag.hasField(FieldKey.ARTIST)) {
                if (tag.hasField(FieldKey.COUNTRY)) {
                    val country = tag.getFirst(FieldKey.COUNTRY)
                    val countryCode = CountryCode.getByCodeIgnoreCase(country)
                    attributes[ARTIST] = ImmutableArtist(tag.getFirst(FieldKey.ARTIST), countryCode)
                } else {
                    attributes[ARTIST] = ImmutableArtist(tag.getFirst(FieldKey.ARTIST))
                }
            }
            if (tag.hasField(FieldKey.GENRE)) {
                attributes[GENRE_NAME] = Genre.parseGenre(tag.getFirst(FieldKey.GENRE)).name
            }
            if (tag.hasField(FieldKey.COMMENT)) {
                attributes[COMMENTS] = tag.getFirst(FieldKey.COMMENT)
            }
            if (tag.hasField(FieldKey.ENCODER)) {
                attributes[ENCODER] = tag.getFirst(FieldKey.ENCODER)
            }
            if (tag.hasField(FieldKey.BPM)) {
                try {
                    val bpm = tag.getFirst(FieldKey.BPM).toInt()
                   attributes[BPM] = (if (bpm < 1) 0 else bpm).toFloat()
                } catch (_: NumberFormatException) { }
            }
            if (tag.hasField(FieldKey.DISC_NO)) {
                try {
                    val dn = tag.getFirst(FieldKey.DISC_NO).toShort()
                    attributes[DISC_NUMBER] = (if (dn < 1) 0 else dn)
                } catch (_: NumberFormatException) { }
            }
            if (tag.hasField(FieldKey.TRACK)) {
                try {
                    val trackNumber = tag.getFirst(FieldKey.TRACK).toShort()
                    attributes[TRACK_NUMBER] = (if (trackNumber < 1) 0 else trackNumber)
                } catch (_: NumberFormatException) { }
            }
        }

        private fun readAlbum(attributes: AudioItemAttributes, extension: String, tag: Tag) {
            val album: Album
            var albumName = ""
            var albumArtist = ImmutableArtist.UNKNOWN_ARTIST
            var isCompilation = false
            var year: Short = -1
            var label = ImmutableLabel.UNKNOWN
            var coverBytes: ByteArray?  = null
            if (tag.hasField(FieldKey.ALBUM)) albumName = tag.getFirst(FieldKey.ALBUM)
            if (tag.hasField(FieldKey.ALBUM_ARTIST)) {
                val artistAlbumName = tag.getFirst(FieldKey.ALBUM_ARTIST)
                albumArtist = ImmutableArtist(artistAlbumName)
            }
            if (tag.hasField(FieldKey.GROUPING)) {
                val labelName = tag.getFirst(FieldKey.GROUPING)
                label = ImmutableLabel(labelName)
            }
            if (tag.hasField(FieldKey.YEAR)) {
                try {
                    year = tag.getFirst(FieldKey.YEAR).toShort()
                } catch (_: NumberFormatException) { }
            }
            if (tag.hasField(FieldKey.IS_COMPILATION)) {
                isCompilation =
                    if ("m4a" == extension) "1" == tag.getFirst(FieldKey.IS_COMPILATION)
                    else "true" == tag.getFirst(FieldKey.IS_COMPILATION)
            }
            if (tag.artworkList.isNotEmpty()) coverBytes = tag.firstArtwork.binaryData
            album = ImmutableAlbum(albumName, albumArtist, isCompilation, year, label, coverBytes)
            attributes[ALBUM] = album
        }
    }
}