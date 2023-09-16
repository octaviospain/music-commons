package net.transgressoft.commons.music

import com.neovisionaries.i18n.CountryCode
import net.transgressoft.commons.music.audio.*
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jetbrains.kotlin.com.google.common.base.CharMatcher
import org.jetbrains.kotlin.com.google.common.base.Splitter
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.regex.Pattern
import kotlin.io.path.extension

object AudioUtils {

    fun readAudioItemFields(audioItemPath: Path): AudioItemBuilder<AudioItem> {
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
        val coverBytes = getCoverBytes(tag)

        val now = LocalDateTime.now()
        return ImmutableAudioItemBuilder()
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

    fun getCoverBytes(audioItem: AudioItem) = getCoverBytes(AudioFileIO.read(audioItem.path.toFile()).tag)

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
            ImmutableArtist(beautifyArtistName(artistName), country)
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
                ImmutableAlbum(this, ImmutableArtist(beautifyArtistName(albumArtistName)), isCompilation, year, label)
            }
        }

    /**********************************************************************************
     *  Function to get artist names in the title, artist field and album artist field
     **********************************************************************************/

    private val endsWithRemix = Pattern.compile("[(|\\[](\\s*(&?\\s*(\\w+)\\s+)+(?i)(remix))[)|\\]]")
    private val startsWithRemixBy = Pattern.compile("[(|\\[](?i)(remix)(\\s+)(?i)(by)(.+)[)|\\]]")
    private val hasFt = Pattern.compile("[(\\[|\\s](?i)(ft) (.+)")
    private val hasFeat = Pattern.compile("[(\\[|\\s](?i)(feat) (.+)")
    private val hasFeaturing = Pattern.compile("[(\\[|\\s](?i)(featuring) (.+)")
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
     * <h2>Example</h2>
     *
     *
     * The following AudioItem instance:
     *
     * audioItem.name = "Who Controls (Adam Beyer Remix)"
     * audioItem.artist = "David Meiser, Black Asteroid & Tiga"
     * audioItem.albumArtist = "Ida Engberg"
     *
     * ... produces the following (without order):
     *
     * `[David Meiser, Black Asteroid, Tiga, Adam Beyer, Ida Engberg]
     *
     * @param title           The title of an audio item
     * @param artistName      The artist name of an audio item
     * @param albumArtistName The album artist name of an audio item
     *
     * @return An `ImmutableSet` object with the names of the artists
     */
    fun getArtistsNamesInvolved(title: String, artistName: String, albumArtistName: String): Set<String> {
        val artistsInvolved: MutableSet<String> = mutableSetOf()
        val albumArtistNames = Splitter.on(CharMatcher.anyOf(",&"))
            .trimResults()
            .omitEmptyStrings()
            .splitToList(albumArtistName)

        artistsInvolved.addAll(albumArtistNames)
        artistsInvolved.addAll(getNamesInArtist(artistName))
        artistsInvolved.addAll(getNamesInTitle(title))
        artistsInvolved.remove("")
        return artistsInvolved
    }

    /**
     * Returns artist names that are in the given artist name.
     * Commonly they can be separated by ',' or '&' characters, or by the words 'versus' or 'vs'.
     *
     * <h3>Example</h3>
     *
     *
     * The given audio item artist field:
     *
     * "David Meiser, Black Asteroid & Tiga"
     *
     * ... produces the following set (without order):
     *
     * [David Meiser, Black Asteroid, Tiga]
     *
     * @param artistName The artist name from where to find more names
     *
     * @return A Set with the artists found
     */
    private fun getNamesInArtist(artistName: String): Set<String> =
        artistName.split("((\\s+(?i)(versus)\\s+)|(\\s+(?i)(vs)(\\.|\\s+))|(\\s+(?i)(feat)(\\.|\\s+))|(\\s+(?i)(ft)(\\.|\\s+))|(\\s*,\\s*)|(\\s+&\\s+))".toRegex())
            .map { it.trim().replaceFirstChar(Char::titlecase) }
            .map { it.split(" ").joinToString(" ") { itt -> itt.replaceFirstChar(Char::titlecase) } }
            .map { beautifyArtistName(it) }
            .toSet()

    /**
     * Returns the names of the artists that are in a given string which is the title of an [AudioItem].
     * For example:
     *
     * The following audio item name field:
     *
     * Song name (Adam Beyer & Pete Tong Remix)
     *
     * ... produces the following (without order):
     *
     * [Adam Beyer, Pete Tong]
     *
     * @param title The `String` where to find artist names
     *
     * @return A Set with the artists found
     */
    private fun getNamesInTitle(title: String): Set<String> {
        val artistsInsideParenthesis = mutableSetOf<String>()
        for ((keyPattern, value) in artistsRegexMap) {
            val matcher = value.matcher(title)
            if (matcher.find()) {
                val insideParenthesisString = title.substring(matcher.start())
                    .replace("[(\\[|)\\]]".toRegex(), "")
                    .replace(keyPattern.pattern().toRegex(), "")
                    .replace("\\s(?i)(vs)\\s".toRegex(), "&")
                    .replace("\\s+".toRegex(), " ")

                artistsInsideParenthesis.addAll(
                    Splitter.on(CharMatcher.anyOf("&,"))
                        .trimResults()
                        .omitEmptyStrings()
                        .splitToList(insideParenthesisString)
                )
                break
            }
        }
        return artistsInsideParenthesis
            .map { it.split(" ").joinToString(" ") { itt -> itt.replaceFirstChar(Char::titlecase) } }
            .toSet()
    }

    fun beautifyArtistName(name: String): String {
        return name.replaceFirstChar(Char::titlecase)
            .replace("\\s+".toRegex(), " ")
            .replace(" (?i)(vs)(\\.|\\s)".toRegex(), " vs ")
            .replace(" (?i)(versus) ".toRegex(), " versus ")
    }
}