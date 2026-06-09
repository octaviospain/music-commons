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

import net.transgressoft.lirp.persistence.ColumnConverter
import net.transgressoft.lirp.persistence.ColumnType
import java.net.URI
import java.nio.file.Path

/**
 * Maps [Path] values to URI-form strings for SQL persistence, matching the existing JSON wire
 * format used by audio item serialization. The URI encoding preserves platform-independent
 * path semantics and round-trips reliably across local file paths.
 */
object PathConverter : ColumnConverter<Path, String> {

    override val sqlType = ColumnType.TextType

    override fun toSql(value: Path): String = value.toUri().toString()

    override fun fromSql(raw: String): Path = Path.of(URI(raw))
}