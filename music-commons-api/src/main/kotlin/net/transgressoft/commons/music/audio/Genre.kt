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
 * Represents a music genre as a sealed type hierarchy.
 *
 * Known genres are predefined [data object] singletons from the
 * [whatlastgenre whitelist](https://github.com/YetAnotherNerd/whatlastgenre).
 * Unrecognized genre strings are preserved via [Custom] instead of being
 * mapped to a lossy fallback.
 *
 * @property name display-friendly genre name (e.g. "Hip Hop", "Drum And Bass")
 */
sealed class Genre(open val name: String) {

    data class Custom(override val name: String) : Genre(name) {
        init {
            require("," !in name) { "Custom genre name must not contain commas: '$name'" }
        }
    }
}