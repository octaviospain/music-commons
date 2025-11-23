/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import org.jetbrains.kotlin.com.google.common.base.CharMatcher
import org.jetbrains.kotlin.com.google.common.base.Splitter
import java.util.regex.Pattern
import kotlin.collections.iterator

/**
 * Utility object providing helper functions for audio item operations.
 *
 * Includes comparators for sorting audio items and artist name extraction utilities
 * that parse various metadata fields to identify all artists involved in a track.
 */
object AudioUtils {

    /**
     * Creates a comparator for sorting audio items by disc and track numbers.
     *
     * Items are first sorted by disc number, then by track number. Null values
     * are considered greater than non-null values, placing items without disc/track
     * numbers at the end of the sorted collection.
     */
    fun <I: ReactiveAudioItem<I>> audioItemTrackDiscNumberComparator() =
        Comparator<I> { audioItem1, audioItem2 ->
            // Compare disc numbers first
            val discNumberComparison = compareDiscNumbers(audioItem1.discNumber, audioItem2.discNumber)

            // If disc numbers are equal, compare track numbers
            if (discNumberComparison == 0) {
                compareTrackNumbers(audioItem1.trackNumber, audioItem2.trackNumber)
            } else {
                discNumberComparison
            }
        }

    private fun compareDiscNumbers(disc1: Short?, disc2: Short?): Int =
        when {
            disc1 == null && disc2 == null -> 0
            disc1 == null -> 1
            disc2 == null -> -1
            else -> disc1 - disc2
        }

    private fun compareTrackNumbers(track1: Short?, track2: Short?): Int =
        when {
            track1 == null && track2 == null -> 0
            track1 == null -> 1
            track2 == null -> -1
            else -> track1 - track2
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
     * Returns the names of the artists that are involved in the fields of an [net.transgressoft.commons.music.audio.AudioItem],
     * that is, every artist that could appear in the [ReactiveAudioItem.artist] variable,
     * or [net.transgressoft.commons.music.audio.Album.albumArtist] or in the [ReactiveAudioItem.title].
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
        val albumArtistNames: Collection<String> = Splitter.on(CharMatcher.anyOf(",&")).trimResults().omitEmptyStrings().splitToList(albumArtistName)

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
    private fun getNamesInArtist(artistName: String): Set<String> {
        // Define regex patterns for different separators
        val versusPattern = "\\s+(?i)(versus)\\s+"
        val vsPattern = "\\s+(?i)(vs)(\\.|\\s+)"
        val featPattern = "\\s+(?i)(feat)(\\.|\\s+)"
        val ftPattern = "\\s+(?i)(ft)(\\.|\\s+)"
        val commaPattern = "\\s*,\\s*"
        val ampersandPattern = "\\s+&\\s+"

        // Combine patterns
        val separatorPattern = "($versusPattern|$vsPattern|$featPattern|$ftPattern|$commaPattern|$ampersandPattern)"

        return artistName.split(separatorPattern.toRegex())
            .map { it.trim().replaceFirstChar(Char::titlecase) }
            .map {
                it.split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar(Char::titlecase)
                    }
            }
            .map { beautifyArtistName(it) }
            .toSet()
    }

    /**
     * Returns the names of the artists that are in a given string which is the title of an [net.transgressoft.commons.music.audio.AudioItem].
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

    /**
     * Standardizes artist name formatting for consistency.
     *
     * Normalizes whitespace, capitalizes the first character, and standardizes
     * separators like "vs" and "versus" to ensure a uniform presentation.
     */
    fun beautifyArtistName(name: String): String =
        name.replaceFirstChar(Char::titlecase)
            .replace("\\s+".toRegex(), " ")
            .replace(" (?i)(vs)(\\.|\\s)".toRegex(), " vs ")
            .replace(" (?i)(versus) ".toRegex(), " versus ")
}