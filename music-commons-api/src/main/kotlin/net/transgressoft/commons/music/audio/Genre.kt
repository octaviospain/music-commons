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
 * Represents a music genre as a sealed type hierarchy with natural ordering by name.
 *
 * Known genres are predefined [data object] singletons from the
 * [whatlastgenre whitelist](https://github.com/YetAnotherNerd/whatlastgenre).
 * Unrecognized genre strings are preserved via [Custom] instead of being
 * mapped to a lossy fallback.
 *
 * The natural ordering is case-sensitive and based on [name], matching the convention
 * established by [Album]. All subtypes inherit [compareTo] from this sealed base.
 *
 * @property name display-friendly genre name (e.g. "Hip Hop", "Drum And Bass")
 */
sealed class Genre(open val name: String) : Comparable<Genre> {

    override fun compareTo(other: Genre): Int = name.compareTo(other.name)

    /**
     * Projection-only sentinel that buckets tracks with no genre tags into a dedicated no-genre
     * index. Its empty name mirrors the [Artist.UNKNOWN] empty-named unknown-entity convention
     * and sorts first by natural name order as a consequence.
     *
     * [None] is never parsed, never persisted, and never assigned to an item's `genres` set;
     * it exists solely so the genre index can surface untagged tracks rather than dropping them.
     */
    data object None : Genre("")

    data class Custom(override val name: String) : Genre(name) {
        init {
            // A blank name is reserved for the [None] sentinel: an empty name is compareTo-equal to None
            // and a whitespace-only name is unreachable via the blank-name query helpers (which route to
            // None), so either would be a lossy, unqueryable custom genre.
            require(name.isNotBlank()) { "Custom genre name must not be blank" }
            require("," !in name) { "Custom genre name must not contain commas: '$name'" }
        }
    }
}