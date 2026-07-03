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

package net.transgressoft.commons.music.audio

import java.util.regex.Pattern

/**
 * Creates a comparator for sorting audio items by disc and track numbers.
 *
 * Items are first sorted by disc number, then by track number. Null values are considered
 * greater than non-null values, placing items without disc/track numbers at the end.
 */
fun <I : ReactiveAudioItem<I>> audioItemTrackDiscNumberComparator(): Comparator<I> =
    Comparator { audioItem1, audioItem2 ->
        val discNumberComparison = compareDiscNumbers(audioItem1.discNumber, audioItem2.discNumber)
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

/**
 * Creates a comparator for sorting audio items by primary artist name, then album name, then disc and
 * track number.
 *
 * This ordering is suitable for flat cross-album indexes (e.g. genre indexes) where items span
 * multiple artists and albums and a predictable, musically meaningful traversal order is needed.
 * Album ordering is by album name specifically — not the natural [AlbumDetails] ordering, which
 * tie-breaks on label and year before name — so the traversal matches the name-based grouping that
 * genre-index consumers expect.
 */
fun <I : ReactiveAudioItem<I>> audioItemArtistAlbumTrackComparator(): Comparator<I> =
    compareBy<I>({ it.artist }, { it.album.name }).thenComparing(audioItemTrackDiscNumberComparator())

/**
 * Creates a comparator for ordering album buckets by album title, then album artist, then release year.
 *
 * This is a **bucket-level** comparator that operates on [ReactiveAlbum] values, comparing the
 * representative [AlbumDetails] exposed by each bucket. It is distinct from the within-bucket
 * comparators ([audioItemTrackDiscNumberComparator]) that order tracks inside a single album.
 *
 * Ordering rules:
 * - Primary key: album name, trimmed and case-insensitive ascending. Buckets whose name is blank
 *   (including [AlbumDetails.UNKNOWN]) sort **last** so that untagged albums appear at the end.
 * - Secondary key: album artist name, trimmed and case-insensitive ascending.
 * - Tertiary key: release year ascending (earliest first); a null year sorts after a non-null year.
 *
 * The comparator reads sort fields from the bucket's representative value ([ReactiveAlbum.album])
 * and does not add a final identity tie-break — the projection framework appends its own
 * natural-order key tiebreak to guarantee that equal-comparing distinct buckets are both retained.
 *
 * The generic bound `RA : ReactiveAlbum<RA, *>` means one definition serves both the core
 * [Album] type and the FX `ObservableAlbum` type without duplication.
 */
fun <RA : ReactiveAlbum<RA, *>> albumBucketComparator(): Comparator<RA> =
    Comparator { a, b ->
        val nameA = a.album.name
        val nameB = b.album.name
        val blankA = nameA.isBlank()
        val blankB = nameB.isBlank()

        // Both blank — treat as equal; lirp appends its own key tiebreak
        if (blankA && blankB) return@Comparator 0
        // One blank sorts last
        if (blankA) return@Comparator 1
        if (blankB) return@Comparator -1

        val nameComparison = nameA.trim().compareTo(nameB.trim(), ignoreCase = true)
        if (nameComparison != 0) return@Comparator nameComparison

        val artistComparison = compareAlbumArtistNames(a.album.albumArtist.name, b.album.albumArtist.name)
        if (artistComparison != 0) return@Comparator artistComparison

        compareAlbumYears(a.album.year, b.album.year)
    }

private fun compareAlbumArtistNames(name1: String, name2: String): Int =
    name1.trim().compareTo(name2.trim(), ignoreCase = true)

private fun compareAlbumYears(year1: Short?, year2: Short?): Int =
    when {
        year1 == null && year2 == null -> 0
        year1 == null -> 1
        year2 == null -> -1
        else -> year1 - year2
    }

/**
 * Creates a comparator for ordering album projection buckets by their canonical [AlbumDetails] key.
 *
 * This is a **key-level** comparator that operates on [AlbumDetails] canonical bucket keys
 * (as produced by [AlbumDetails.canonicalKey]). It applies the same name-first, artist-second
 * ordering as [albumBucketComparator] but on the already-normalized canonical key rather than
 * on the representative value — making it suitable as `bucketKeyOrdering` in [registryProjection].
 *
 * Ordering rules:
 * - Primary key: album name ascending, blank last.
 * - Secondary key: album artist name ascending.
 *
 * The canonical key always has `year = null` and `label = UNKNOWN`, so year is not a
 * meaningful tiebreak at this level; the projection framework's mandatory natural-order final
 * tiebreak handles any remaining collisions.
 */
fun albumCanonicalKeyComparator(): Comparator<AlbumDetails> =
    Comparator { a, b ->
        val nameA = a.name
        val nameB = b.name
        val blankA = nameA.isBlank()
        val blankB = nameB.isBlank()

        if (blankA && blankB) return@Comparator 0
        if (blankA) return@Comparator 1
        if (blankB) return@Comparator -1

        val nameComparison = nameA.compareTo(nameB, ignoreCase = true)
        if (nameComparison != 0) return@Comparator nameComparison

        compareAlbumArtistNames(a.albumArtist.name, b.albumArtist.name)
    }

/**
 * Creates an identity tie-break comparator that distinguishes between two distinct audio items
 * whose primary comparator returns 0.
 *
 * This comparator exists so that flat-bucket [java.util.TreeSet]s retain both items when their
 * primary sort key is identical (e.g. two different tracks with the same disc and track number).
 * When both items have an assigned id, comparison is by id; otherwise it falls back to [ReactiveAudioItem.uniqueId].
 */
fun <I : ReactiveAudioItem<I>> audioItemIdentityComparator(): Comparator<I> =
    Comparator { a, b ->
        when {
            a.id != UNASSIGNED_ID && b.id != UNASSIGNED_ID -> a.id.compareTo(b.id)
            else -> a.uniqueId.compareTo(b.uniqueId)
        }
    }

/**
 * Returns the cover image bytes of the first item in [items] that has a non-null cover, or `null`
 * if no item carries cover data.
 *
 * Iterates [items] in their natural iteration order and returns the first non-null
 * [ReactiveAudioItem.coverImageBytes]. Accessing an item's cover may trigger a lazy load from the
 * underlying audio file; this function does not cache the result beyond what each item's own
 * implementation already does.
 *
 * @param items The collection of audio items to search.
 * @return The raw cover image bytes of the first covered item, or `null`.
 */
fun <I : ReactiveAudioItem<I>> firstCoverImageBytes(items: Iterable<I>): ByteArray? {
    for (item in items) {
        val bytes = item.coverImageBytes
        if (bytes != null) return bytes
    }
    return null
}

/**********************************************************************************
 *  Functions to get artist names in the title, artist field and album artist field
 **********************************************************************************/

private val endsWithRemix = Pattern.compile("[(|\\[](\\s*(&?\\s*([\\w.]+)\\s+)+(?i)(remix))[)|\\]]")
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
 * Returns the names of the artists involved in the fields of an audio item — every artist
 * that could appear in [artistName], [albumArtistName], or [title].
 *
 * <h2>Example</h2>
 *
 * The following AudioItem instance:
 *
 * audioItem.name = "Who Controls (Adam Beyer Remix)"
 * audioItem.artist = "David Meiser, Black Asteroid & Tiga"
 * audioItem.albumArtist = "Ida Engberg"
 *
 * ... produces the following (without order):
 *
 * `[David Meiser, Black Asteroid, Tiga, Adam Beyer, Ida Engberg]`
 *
 * @param title           The title of an audio item
 * @param artistName      The artist name of an audio item
 * @param albumArtistName The album artist name of an audio item
 *
 * @return A `Set` with the names of the artists
 */
fun getArtistsNamesInvolved(title: String, artistName: String, albumArtistName: String): Set<String> {
    val artistsInvolved: MutableSet<String> = mutableSetOf()
    val albumArtistNames: Collection<String> = albumArtistName.split("&", ",").map { it.trim() }.filter { it.isNotEmpty() }

    artistsInvolved.addAll(albumArtistNames)
    artistsInvolved.addAll(getNamesInArtist(artistName))
    artistsInvolved.addAll(getNamesInTitle(title))
    artistsInvolved.remove("")
    return artistsInvolved
}

private fun getNamesInArtist(artistName: String): Set<String> {
    val versusPattern = "\\s+(?i)(versus)\\s+"
    val vsPattern = "\\s+(?i)(vs)(\\.|\\s+)"
    val featPattern = "\\s+(?i)(feat)(\\.|\\s+)"
    val ftPattern = "\\s+(?i)(ft)(\\.|\\s+)"
    val commaPattern = "\\s*,\\s*"
    val ampersandPattern = "\\s+&\\s+"

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

private fun getNamesInTitle(title: String): Set<String> {
    val artists = mutableSetOf<String>()
    var remainingTitle = title

    for ((keyPattern, valuePattern) in artistsRegexMap) {
        if (valuePattern === hasFt || valuePattern === hasFeat || valuePattern === hasFeaturing) continue
        val matcher = valuePattern.matcher(remainingTitle)
        if (matcher.find()) {
            val insideParenthesisString =
                remainingTitle.substring(matcher.start()).replace("[(\\[|)\\]]".toRegex(), "")
                    .replace(keyPattern.pattern().toRegex(), "")
                    .replace("\\s(?i)(vs)\\s".toRegex(), "&").replace("\\s+".toRegex(), " ")
            artists.addAll(
                insideParenthesisString.split("&", ",").map { it.trim() }.filter { it.isNotEmpty() }
            )
            remainingTitle = remainingTitle.substring(0, matcher.start()).trimEnd()
            break
        }
    }

    for ((keyPattern, valuePattern) in artistsRegexMap) {
        if (valuePattern !== hasFt && valuePattern !== hasFeat && valuePattern !== hasFeaturing) continue
        val matcher = valuePattern.matcher(remainingTitle)
        if (matcher.find()) {
            val insideParenthesisString =
                remainingTitle.substring(matcher.start()).replace("[(\\[|)\\]]".toRegex(), "")
                    .replace(keyPattern.pattern().toRegex(), "")
                    .replace("\\s(?i)(vs)\\s".toRegex(), "&").replace("\\s+".toRegex(), " ")
            artists.addAll(
                insideParenthesisString.split("&", ",").map { it.trim() }.filter { it.isNotEmpty() }
            )
            break
        }
    }

    return artists
        .map { it.split(" ").joinToString(" ") { itt -> itt.replaceFirstChar(Char::titlecase) } }
        .toSet()
}

/**
 * Standardizes artist name formatting for consistency.
 *
 * Normalizes whitespace, capitalizes the first character, and standardizes separators like
 * "vs" and "versus" to ensure a uniform presentation.
 */
fun beautifyArtistName(name: String): String =
    name.replaceFirstChar(Char::titlecase)
        .replace("\\s+".toRegex(), " ")
        .replace(" (?i)(vs)(\\.|\\s)".toRegex(), " vs ")
        .replace(" (?i)(versus) ".toRegex(), " versus ")