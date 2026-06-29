/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

/**
 * Derives a representative [AlbumDetails] from a bucket of tracks.
 *
 * Each field is set to the most-frequent non-empty value across the tracks (so a majority of
 * correctly-tagged tracks outweigh a single mistag). Tie-breaking rules:
 * - `year` → earliest
 * - `label` / `albumArtist` (non-compilation) → lexicographically smallest of equal-frequency values
 * - display name casing → most-frequent, then first-seen casing (after trimming and collapsing
 *   internal whitespace so the exposed name carries no stray spacing)
 *
 * `isCompilation` is `true` when any track is compilation-like (see [AlbumDetails.isCompilationAlbum]);
 * in that case `albumArtist` collapses to [Artist.UNKNOWN], mirroring the canonical bucket key.
 *
 * This is a pure function of [tracks]: it reads no external state and has no side effects.
 * Both the core and FX registries call it from their value-transform lambda.
 */
fun <I : ReactiveAudioItem<I>> deriveRepresentativeAlbumDetails(tracks: List<I>): AlbumDetails {
    val name =
        mostFrequent(
            tracks.map { it.album.name.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }
        ) ?: ""
    val isCompilation = tracks.any { it.album.isCompilationAlbum() }
    val albumArtist =
        if (isCompilation) Artist.UNKNOWN
        else mostFrequentBy(tracks.map { it.album.albumArtist }) { it.id() } ?: Artist.UNKNOWN
    val year =
        tracks.mapNotNull { it.album.year }
            .groupingBy { it }
            .eachCount()
            .let { freq ->
                val maxCount = freq.values.maxOrNull() ?: return@let null
                freq.entries.filter { it.value == maxCount }.minByOrNull { it.key }?.key
            }
    val label =
        tracks.map { it.album.label }
            .filter { it != Label.UNKNOWN }
            .let { labels ->
                if (labels.isEmpty()) Label.UNKNOWN
                else mostFrequentBy(labels) { it.name } ?: Label.UNKNOWN
            }
    return AlbumDetails(name, albumArtist, isCompilation, year, label)
}

// Returns the most-frequent string value from [values]; when frequencies tie, returns
// the first-seen value among the tied ones (preserving original casing for display name).
private fun mostFrequent(values: List<String>): String? {
    if (values.isEmpty()) return null
    val freq = values.groupingBy { it }.eachCount()
    val maxCount = freq.values.max()
    return values.first { freq[it] == maxCount }
}

// Returns the most-frequent value from [values] by key produced by [keyFn]; on tie, the
// lexicographically smallest key wins (deterministic, independent of insertion order).
private fun <T> mostFrequentBy(values: List<T>, keyFn: (T) -> String): T? {
    if (values.isEmpty()) return null
    val freq = values.groupingBy(keyFn).eachCount()
    val maxCount = freq.values.max()
    val winnerKey = freq.entries.filter { it.value == maxCount }.minByOrNull { it.key }!!.key
    return values.first { keyFn(it) == winnerKey }
}