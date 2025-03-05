package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import org.jetbrains.kotlin.com.google.common.base.CharMatcher
import org.jetbrains.kotlin.com.google.common.base.Splitter
import java.util.regex.Pattern

object AudioUtils {

    fun <I: ReactiveAudioItem<I>> audioItemTrackDiscNumberComparator() =
        Comparator<I> { audioItem1, audioItem2 ->
            when {
                audioItem1.discNumber == null && audioItem2.discNumber == null -> { // Both discNumbers are null, compare by trackNumber
                    when {
                        audioItem1.trackNumber == null && audioItem2.trackNumber == null -> 0
                        audioItem1.trackNumber == null -> 1
                        audioItem2.trackNumber == null -> -1
                        else -> audioItem1.trackNumber!! - audioItem2.trackNumber!!
                    }
                }

                audioItem1.discNumber == null -> 1
                audioItem2.discNumber == null -> -1
                else -> { // Compare non-null discNumbers
                    if (audioItem1.discNumber == audioItem2.discNumber) { // If discNumbers are equal, compare by trackNumber
                        when {
                            audioItem1.trackNumber == null && audioItem2.trackNumber == null -> 0
                            audioItem1.trackNumber == null -> 1
                            audioItem2.trackNumber == null -> -1
                            else -> audioItem1.trackNumber!! - audioItem2.trackNumber!!
                        }
                    } else { // Different discNumbers, compare by discNumber
                        audioItem1.discNumber!! - audioItem2.discNumber!!
                    }
                }
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

    private val artistsRegexMap: Map<Pattern, Pattern> =
        buildMap {
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
        val albumArtistNames = Splitter.on(CharMatcher.anyOf(",&")).trimResults().omitEmptyStrings().splitToList(albumArtistName)

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
        artistName.split(
            "((\\s+(?i)(versus)\\s+)|(\\s+(?i)(vs)(\\.|\\s+))|(\\s+(?i)(feat)(\\.|\\s+))|(\\s+(?i)(ft)(\\.|\\s+))|(\\s*,\\s*)|(\\s+&\\s+))".toRegex()
        )
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
                val insideParenthesisString =
                    title.substring(matcher.start()).replace("[(\\[|)\\]]".toRegex(), "").replace(keyPattern.pattern().toRegex(), "")
                        .replace("\\s(?i)(vs)\\s".toRegex(), "&").replace("\\s+".toRegex(), " ")

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

    fun beautifyArtistName(name: String): String =
        name.replaceFirstChar(Char::titlecase)
            .replace("\\s+".toRegex(), " ")
            .replace(" (?i)(vs)(\\.|\\s)".toRegex(), " vs ")
            .replace(" (?i)(versus) ".toRegex(), " versus ")
}