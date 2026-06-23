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

package net.transgressoft.commons.persistence.music.audio

import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.parseGenre
import net.transgressoft.lirp.persistence.ColumnConverter
import net.transgressoft.lirp.persistence.ColumnType
import kotlinx.serialization.json.Json

/**
 * Maps individual [Genre] elements to their display-name string for SQL persistence.
 *
 * Each element is serialized as the genre's display name string. On read, the name is resolved
 * through [parseGenre] — the single source of truth for genre-string mapping shared with audio
 * metadata parsing and JSON persistence — so a name that matches a standard genre
 * (case-insensitively) is canonicalized to that standard genre, and any name outside the built-in
 * registry is preserved as [Genre.Custom]. A custom name that collides with a standard genre is
 * therefore intentionally normalized to the standard subtype, keeping persistence consistent with
 * how the same string resolves everywhere else in the library. Individual genre names never contain
 * commas (enforced by [Genre.Custom]'s init block), so per-element encoding is unambiguous.
 */
object GenreConverter : ColumnConverter<Genre, String> {

    override val sqlType = ColumnType.TextType

    override fun toSql(value: Genre): String = value.name

    override fun fromSql(raw: String): Genre = parseGenre(raw).firstOrNull() ?: Genre.Custom(raw)

    /**
     * Encodes a genre set as a JSON array of display names, sorted for a deterministic column value.
     *
     * @param genres the genres of an audio item
     * @return a JSON string array of genre names
     */
    fun encodeGenres(genres: Set<Genre>): String = Json.encodeToString(genres.map { toSql(it) }.sorted())

    /**
     * Decodes a JSON array of genre names back into a genre set, dropping blank entries so a stray
     * empty name never materializes a degenerate [Genre.Custom].
     *
     * @param raw the stored JSON string array
     * @return the reconstructed genre set
     */
    fun decodeGenres(raw: String): Set<Genre> =
        Json.decodeFromString<List<String>>(raw)
            .filter { it.isNotBlank() }
            .map { fromSql(it) }
            .toSet()
}