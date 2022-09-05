package net.transgressoft.commons.music.audio

import com.google.common.base.CharMatcher
import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import net.transgressoft.commons.query.EntityAttribute
import net.transgressoft.commons.query.QueryEntity
import org.apache.commons.io.FilenameUtils
import org.apache.commons.text.WordUtils
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * @author Octavio Calleya
 */
class ImmutableAudioItem(override val id: Int, attributes: AudioItemAttributes) : AudioItem, Comparable<AudioItem> {
    private val path: Path
    private val title: String
    private val artist: Artist
    private val artistsInvolved: Set<String>
    private val album: Album
    private val genre: Genre
    private val comments: String
    private val trackNumber: Short
    private val discNumber: Short
    private val bpm: Float
    private val duration: Duration
    private val bitRate: Int
    private val encoder: String
    private val encoding: String
    private val dateOfCreation: LocalDateTime
    private val lastDateModified: LocalDateTime
    private val attributes: AudioItemAttributes

    override val uniqueId: String
        get() = StringJoiner("-")
            .add(fileName().replace(' ', '_'))
            .add(title)
            .add(duration.toString())
            .add(bitRate().toString())
            .toString()

    override fun path(): Path {
        return path
    }

    override fun path(path: Path): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(AudioItemPathAttribute.PATH, path))
    }

    override fun fileName(): String {
        return path.fileName.toString()
    }

    override fun extension(): String {
        return FilenameUtils.getExtension(path.toString())
    }

    override fun title(): String {
        return title
    }

    override fun title(title: String): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(AudioItemStringAttribute.TITLE, title))
    }

    override fun artist(): Artist {
        return artist
    }

    override fun artist(artist: Artist): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(ArtistAttribute.ARTIST, artist))
    }

    override fun artistsInvolved(): ImmutableSet<String> {
        return ImmutableSet.copyOf(artistsInvolved)
    }

    override fun album(): Album {
        return album
    }

    override fun album(album: Album): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(AlbumAttribute.ALBUM, album))
    }

    override fun genre(): Genre {
        return genre
    }

    override fun genre(genre: Genre): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(AudioItemStringAttribute.GENRE_NAME, genre.name))
    }

    override fun comments(): String {
        return comments
    }

    override fun comments(comments: String): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(AudioItemStringAttribute.COMMENTS, comments))
    }

    override fun trackNumber(): Short {
        return trackNumber
    }

    override fun trackNumber(trackNumber: Short): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(AudioItemShortAttribute.TRACK_NUMBER, trackNumber))
    }

    override fun discNumber(): Short {
        return discNumber
    }

    override fun discNumber(discNumber: Short): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(AudioItemShortAttribute.DISC_NUMBER, discNumber))
    }

    override fun bpm(): Float {
        return bpm
    }

    override fun bpm(bpm: Float): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(AudioItemFloatAttribute.BPM, bpm))
    }

    override fun duration(): Duration {
        return duration
    }

    override fun length(): Long {
        return path.toFile().length()
    }

    override fun bitRate(): Int {
        return bitRate
    }

    override fun encoder(): String {
        return encoder
    }

    override fun encoder(encoder: String): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(AudioItemStringAttribute.ENCODER, encoder))
    }

    override fun encoding(): String {
        return encoding
    }

    override fun encoding(encoding: String): AudioItem {
        return ImmutableAudioItem(id, attributes.modifiedCopy(AudioItemStringAttribute.ENCODING, encoding))
    }

    override fun dateOfInclusion(): LocalDateTime {
        return dateOfCreation
    }

    override fun lastDateModified(): LocalDateTime {
        return lastDateModified
    }

    override fun <A : EntityAttribute<V>, V> getAttribute(attribute: A): V {
        return attributes.get(attribute)
    }

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

    override operator fun compareTo(other: AudioItem) =
        Comparator.comparing(QueryEntity::uniqueId, java.lang.String.CASE_INSENSITIVE_ORDER).compare(this, other)

    override fun hashCode(): Int =
        Objects.hashCode(path, title, artist, album, genre, comments, trackNumber, discNumber, bpm, duration)

    override fun toString(): String =
        MoreObjects.toStringHelper(this)
            .add("path", path)
            .add("name", title)
            .add("artist", artist)
            .toString()

    init {
        this.attributes = attributes.copy()
        path = this.attributes[AudioItemPathAttribute.PATH]
        title = this.attributes[AudioItemStringAttribute.TITLE]
        artist = this.attributes[ArtistAttribute.ARTIST]
        album = this.attributes[AlbumAttribute.ALBUM]
        genre = Genre.parseGenre(this.attributes[AudioItemStringAttribute.GENRE_NAME])
        comments = this.attributes[AudioItemStringAttribute.COMMENTS]
        trackNumber = this.attributes[AudioItemShortAttribute.TRACK_NUMBER]
        discNumber = this.attributes[AudioItemShortAttribute.DISC_NUMBER]
        bpm = this.attributes[AudioItemFloatAttribute.BPM]
        duration = this.attributes[AudioItemDurationAttribute.DURATION]
        bitRate = this.attributes[AudioItemIntegerAttribute.BITRATE]
        encoder = this.attributes[AudioItemStringAttribute.ENCODER]
        encoding = this.attributes[AudioItemStringAttribute.ENCODING]

        this.attributes[AudioItemStringAttribute.LABEL_NAME] = album.label.name
        this.attributes[AudioItemShortAttribute.YEAR] = album.year ?: -1
        this.attributes[ArtistAttribute.ALBUM_ARTIST] = album.albumArtist
        this.attributes[ArtistsInvolvedAttribute.ARTISTS_INVOLVED] = getArtistsNamesInvolved(title, artist.name, album.albumArtist.name)
        artistsInvolved = this.attributes[ArtistsInvolvedAttribute.ARTISTS_INVOLVED]

        val now = LocalDateTime.now()
        this.attributes.putIfAbsent(AudioItemLocalDateTimeAttribute.DATE_OF_CREATION, now)
        dateOfCreation = this.attributes[AudioItemLocalDateTimeAttribute.DATE_OF_CREATION]
        this.attributes[AudioItemLocalDateTimeAttribute.LAST_DATE_MODIFIED] = now
        lastDateModified = this.attributes[AudioItemLocalDateTimeAttribute.LAST_DATE_MODIFIED]
    }
}

private val endsWithRemix = Pattern.compile("[(|\\[](\\s*(&?\\s*(\\w+)\\s+)+(?i)(remix))[)|\\]]")
private val startsWithRemixBy = Pattern.compile("[(|\\[](?i)(remix)(\\s+)(?i)(by)(.+)[)|\\]]")
private val hasFt = Pattern.compile("[(|\\[|\\s](?i)(ft) (.+)")
private val hasFeat = Pattern.compile("[(|\\[|\\s](?i)(feat) (.+)")
private val hasFeaturing = Pattern.compile("[(|\\[|\\s](?i)(featuring) (.+)")
private val startsWithWith = Pattern.compile("[(|\\[](?i)(with) (.+)[)|\\]]")

private val artistsRegexMap: Map<Pattern, Pattern>? =
    ImmutableMap.builder<Pattern, Pattern>()
        .put(Pattern.compile(" (?i)(remix)"), endsWithRemix)
        .put(Pattern.compile("(?i)(remix)(\\s+)(?i)(by) "), startsWithRemixBy)
        .put(Pattern.compile("(?i)(ft) "), hasFt)
        .put(Pattern.compile("(?i)(feat) "), hasFeat)
        .put(Pattern.compile("(?i)(featuring) "), hasFeaturing)
        .put(Pattern.compile("(?i)(with) "), startsWithWith).build()

/**
 * Returns the names of the artists that are involved in the fields of an [AudioItem],
 * that is, every artist that could appear in the [AudioItem.artist] variable,
 * or [Album.albumArtist] or in the [AudioItem.title].
 *
 * <h3>Example</h3>
 *
 *
 * The following AudioItem instance: <pre>   `audioItem.name = "Who Controls (Adam Beyer Remix)"
 * audioItem.artist = "David Meiser, Black Asteroid & Tiga"
 * audioItem.albumArtist = "Ida Engberg"
 *
`</pre> *
 * ... produces the following (without order): <pre>   `[David Meiser, Black Asteroid, Tiga, Adam Beyer, Ida Engberg]
`</pre> *
 *
 * @param title           The title of an audio item
 * @param artistName      The artist name of an audio item
 * @param albumArtistName The album artist name of an audio item
 *
 * @return An `ImmutableSet` object with the names of the artists
 */
private fun getArtistsNamesInvolved(title: String, artistName: String, albumArtistName: String): Set<String> {
    val artistsInvolved: MutableSet<String> = mutableSetOf()
    val albumArtistNames = Splitter.on(CharMatcher.anyOf(",&"))
        .trimResults()
        .omitEmptyStrings()
        .splitToList(albumArtistName)

    artistsInvolved.addAll(albumArtistNames)
    artistsInvolved.addAll(getNamesInArtist(artistName))
    artistsInvolved.addAll(getNamesInTitle(title))
    return artistsInvolved.stream()
        .map { WordUtils.capitalize(it) }
        .collect(Collectors.toSet())
}

/**
 * Returns artist names that are in the given artist name.
 * Commonly they can be separated by ',' or '&' characters, or by the words 'versus' or 'vs'.
 *
 * <h3>Example</h3>
 *
 *
 * The given audio item artist field: <pre>   `"David Meiser, Black Asteroid & Tiga"
`</pre> *
 * ... produces the following set (without order): <pre>   `[David Meiser, Black Asteroid, Tiga]
`</pre> *
 *
 * @param artistName The artist name from where to find more names
 *
 * @return An [ImmutableSet] with the artists found
 */
private fun getNamesInArtist(artistName: String): Set<String> {
    val artistsInvolved: Set<String>
    val splitNames = Stream.of(" versus ", " vs ")
        .filter { artistName.contains(it) }
        .map {
            Splitter.on(it)
                .trimResults()
                .omitEmptyStrings()
                .splitToList(artistName)
        }.findAny()

    artistsInvolved = splitNames.orElseGet {
        val cleanedArtist = artistName.replace("(?i)(feat)(\\.|\\s+)".toRegex(), ",")
            .replace("(?i)(ft)(\\.|\\s+)".toRegex(), ",")

        Splitter.on(CharMatcher.anyOf(",&"))
            .trimResults()
            .omitEmptyStrings()
            .splitToList(cleanedArtist)
    }.toSet()
    return artistsInvolved;
}

/**
 * Returns the names of the artists that are in a given string which is the title of an [AudioItem].
 * For example:
 *
 *
 * The following audio item name field: <pre>   `"Song name (Adam Beyer & Pete Tong Remix)"
`</pre> *
 * ... produces the following (without order): <pre>   `[Adam Beyer, Pete Tong]
`</pre> *
 *
 * @param title The `String` where to find artist names
 *
 * @return An [ImmutableSet] with the artists found
 */
private fun getNamesInTitle(title: String): Set<String> {
    val artistsInsideParenthesis = mutableSetOf<String>()
    for ((keyPattern, value) in artistsRegexMap!!) {
        val matcher = value.matcher(title)
        if (matcher.find()) {
            val insideParenthesisString = title.substring(matcher.start())
                .replace("[(|\\[|)|\\]]".toRegex(), "")
                .replace(keyPattern.pattern().toRegex(), "")
                .replace("\\s(?i)(vs)\\s".toRegex(), "&")
                .replace("\\s+".toRegex(), " ")

            artistsInsideParenthesis.addAll(
                Splitter.on(CharMatcher.anyOf("&,"))
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(insideParenthesisString))
            break
        }
    }
    return artistsInsideParenthesis
}