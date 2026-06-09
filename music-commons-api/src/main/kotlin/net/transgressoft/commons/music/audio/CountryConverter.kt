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
import com.neovisionaries.i18n.CountryCode

/**
 * Maps [CountryCode] values to alpha-2 strings for SQL persistence.
 *
 * [CountryCode.UNDEFINED] serializes as `"UNDEFINED"` — the ISO 3166-1 alpha-2 lookup
 * (`getByCode`) returns null for this sentinel value, so the fallback `valueOf` (enum constant
 * name lookup) is used to reconstruct it during deserialization.
 */
object CountryConverter : ColumnConverter<CountryCode, String> {

    override val sqlType = ColumnType.TextType

    override fun toSql(value: CountryCode): String = value.getAlpha2()

    // CountryCode.getByCode("UNDEFINED") returns null because "UNDEFINED" is not a valid
    // ISO alpha-2 code; valueOf falls back to enum constant name lookup which always works.
    override fun fromSql(raw: String): CountryCode = CountryCode.getByCode(raw) ?: CountryCode.valueOf(raw)
}