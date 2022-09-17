package net.transgressoft.commons.music.audio

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jetbrains.kotlin.com.google.common.base.CharMatcher
import org.jetbrains.kotlin.com.google.common.base.Splitter
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.io.path.extension

internal class JAudioTaggerMetadataReader(private val audioItemPath: Path) : AudioItemMetadataReader {

    private val title: String by lazy { getFieldIfExisting(FieldKey.TITLE) ?: "" }
    private val artist: Artist by lazy { readArtist() }
    private val artistsInvolved: Set<String> by lazy { getArtistsNamesInvolved(title, artist.name, album.albumArtist.name) }
    private val album: Album by lazy { readAlbum(extension) }
    private val duration: Duration
    private val genre: Genre by lazy { getFieldIfExisting(FieldKey.GENRE)?.let { Genre.parseGenre(it) } ?: Genre.UNDEFINED }
    private val comments: String? by lazy { getFieldIfExisting(FieldKey.COMMENT) }
    private val trackNumber: Short? by lazy { getFieldIfExisting(FieldKey.TRACK)?.takeIf { it != "0" }?.toShort() }
    private val discNumber: Short? by lazy { getFieldIfExisting(FieldKey.DISC_NO)?.takeIf { it != "0" }?.toShort() }
    private val bpm: Float? by lazy { getFieldIfExisting(FieldKey.BPM)?.takeIf { (it != "0") }?.toFloat() }
    private val encoder: String? by lazy { getFieldIfExisting(FieldKey.ENCODER) }
    private var bitRate: Int
    private val encoding: String?

    private val tag: Tag
    private val extension: String = audioItemPath.extension

    init {
        val audioItemFile = audioItemPath.toFile()
        val audioFile = AudioFileIO.read(audioItemFile)
        tag = audioFile.tag
        val audioHeader = audioFile.audioHeader
        encoding = audioHeader.encodingType
        duration = Duration.ofSeconds(audioHeader.trackLength.toLong())
        bitRate = getBitRate(audioHeader)
    }

    override fun readAudioItemAttributes() =
        AudioItemAttributes(
            audioItemPath, title, artist, artistsInvolved, album, genre, comments, trackNumber, discNumber,
            bpm, duration, bitRate, encoder, encoding, LocalDateTime.now()
        )

    private fun getBitRate(audioHeader: AudioHeader): Int {
        val bitRate = audioHeader.bitRate
        return if ("~" == bitRate.substring(0, 1)) {
            bitRate.substring(1).toInt()
        } else {
            bitRate.toInt()
        }
    }

    private fun getFieldIfExisting(fieldKey: FieldKey): String? = tag.hasField(fieldKey).takeIf { true }.run { tag.getFirst(fieldKey) }

    private fun readArtist(): Artist =
        getFieldIfExisting(FieldKey.ARTIST)?.let {
            val country = getFieldIfExisting(FieldKey.COUNTRY)?.let { _country -> Locale.IsoCountryCode.valueOf(_country) }
            ImmutableArtist(beautifyArtistName(it), country)
        } ?: ImmutableArtist("")

    private fun readAlbum(extension: String): Album =
        with(getFieldIfExisting(FieldKey.ALBUM)) {
            return if (this == null) {
                ImmutableAlbum.UNKNOWN
            } else {
                val albumArtistName = getFieldIfExisting(FieldKey.ALBUM_ARTIST) ?: ""
                val isCompilation = getFieldIfExisting(FieldKey.IS_COMPILATION)?.let {
                    if ("m4a" == extension) "1" == tag.getFirst(FieldKey.IS_COMPILATION)
                    else "true" == tag.getFirst(FieldKey.IS_COMPILATION)
                } ?: false
                val year = getFieldIfExisting(FieldKey.YEAR)?.toShort()
                val label = getFieldIfExisting(FieldKey.GROUPING)?.let { ImmutableLabel(it) }
                val coverBytes = tag.artworkList.isNotEmpty().takeIf { true }?.let { tag.firstArtwork.binaryData }
                ImmutableAlbum(this, ImmutableArtist(beautifyArtistName(albumArtistName)), isCompilation, year, label, coverBytes)
            }
        }

    private fun beautifyArtistName(name: String) =
        name.replaceFirstChar(Char::titlecase)
            .replace("\\s+".toRegex(), " ")
            .replace(" (?i)(vs)(\\.|\\s)".toRegex(), " vs ")
            .replace(" (?i)(versus) ".toRegex(), " versus ")
}

private val endsWithRemix = Pattern.compile("[(|\\[](\\s*(&?\\s*(\\w+)\\s+)+(?i)(remix))[)|\\]]")
private val startsWithRemixBy = Pattern.compile("[(|\\[](?i)(remix)(\\s+)(?i)(by)(.+)[)|\\]]")
private val hasFt = Pattern.compile("[(|\\[|\\s](?i)(ft) (.+)")
private val hasFeat = Pattern.compile("[(|\\[|\\s](?i)(feat) (.+)")
private val hasFeaturing = Pattern.compile("[(|\\[|\\s](?i)(featuring) (.+)")
private val startsWithWith = Pattern.compile("[(|\\[](?i)(with) (.+)[)|\\]]")

private val artistsRegexMap: Map<Pattern, Pattern> = buildMap {
    set(Pattern.compile(" (?i)(remix)"), endsWithRemix)
    set(Pattern.compile("(?i)(remix)(\\s+)(?i)(by) "), startsWithRemixBy)
    set(Pattern.compile("(?i)(ft) "), hasFt)
    set(Pattern.compile("(?i)(feat) "), hasFeat)
    set(Pattern.compile("(?i)(featuring) "), hasFeaturing)
    set(Pattern.compile("(?i)(with) "), startsWithWith)
}

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
internal fun getArtistsNamesInvolved(title: String, artistName: String, albumArtistName: String): Set<String> {
    val artistsInvolved: MutableSet<String> = mutableSetOf()
    val albumArtistNames = Splitter.on(CharMatcher.anyOf(",&"))
        .trimResults()
        .omitEmptyStrings()
        .splitToList(albumArtistName)

    artistsInvolved.addAll(albumArtistNames)
    artistsInvolved.addAll(getNamesInArtist(artistName))
    artistsInvolved.addAll(getNamesInTitle(title))
    return artistsInvolved.stream()
        .map { it.replaceFirstChar(Char::titlecase) }
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