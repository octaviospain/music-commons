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

/**
 * Maps individual [Genre] elements to their display-name string for SQL persistence.
 *
 * Each element is serialized as the genre's display name string. Standard genres round-trip via
 * [parseGenre]; custom genres that are not in the built-in registry are reconstructed as
 * [Genre.Custom] instances. Individual genre names never contain commas (enforced by
 * [Genre.Custom]'s init block), so per-element encoding is unambiguous.
 */
object GenreConverter : ColumnConverter<Genre, String> {

    override val sqlType = ColumnType.TextType

    override fun toSql(value: Genre): String = value.name

    override fun fromSql(raw: String): Genre = parseGenre(raw).firstOrNull() ?: Genre.Custom(raw)
}