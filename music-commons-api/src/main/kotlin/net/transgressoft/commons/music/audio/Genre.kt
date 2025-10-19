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

/**
 * Enumeration of music genres.
 */
enum class Genre {

    ROCK,
    UNDEFINED;

    fun capitalize(): String {
        val replaced = name.replace('_', ' ')
        val capitalized = CharArray(replaced.length)
        capitalized[0] = replaced[0].titlecaseChar()
        for (c in 1 until replaced.toCharArray().size) {
            if (replaced[c - 1] == ' ' || replaced[c - 1] == ',') capitalized[c] =
                replaced[c].titlecaseChar() else capitalized[c] = replaced[c].lowercaseChar()
        }
        return String(capitalized)
    }

    companion object {
        @JvmStatic
        fun parseGenre(value: String): Genre {
            for (genre in entries) {
                if (genre.name.equals(value.replace(" ", "_"), ignoreCase = true)) return genre
            }
            return UNDEFINED
        }
    }
}