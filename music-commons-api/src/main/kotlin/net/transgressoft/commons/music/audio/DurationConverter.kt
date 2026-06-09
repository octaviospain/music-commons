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
import java.time.Duration

/**
 * Maps [Duration] values to whole seconds (Long) for SQL persistence.
 *
 * Sub-second precision is intentionally truncated: audio durations derived from file headers
 * are measured in whole seconds in practice, so no information is lost. The [ColumnType.LongType]
 * backing column avoids floating-point imprecision for typical track lengths.
 */
object DurationConverter : ColumnConverter<Duration, Long> {

    override val sqlType = ColumnType.LongType

    override fun toSql(value: Duration): Long = value.toSeconds()

    override fun fromSql(raw: Long): Duration = Duration.ofSeconds(raw)
}