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

import net.transgressoft.commons.util.expungeStaleEntries
import com.neovisionaries.i18n.CountryCode
import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Concrete value type representing a music artist with an optional country of origin.
 *
 * Uses a flyweight pattern over soft references so that identical artists share the same
 * object instance under normal memory conditions, reducing footprint in large audio libraries.
 * The [UNKNOWN] singleton represents an artist whose name and country are unspecified.
 *
 * Access instances via the [of] factory rather than the constructor directly.
 */
@ConsistentCopyVisibility
data class Artist internal constructor(
    val name: String,
    val countryCode: CountryCode = CountryCode.UNDEFINED
) : Comparable<Artist> {

    override fun compareTo(other: Artist): Int {
        val result = compareValues(name, other.name)
        return if (result == 0) compareValues(countryCode, other.countryCode) else result
    }

    companion object {

        @JvmField
        @get:JvmName("UNKNOWN")
        val UNKNOWN: Artist = Artist("", CountryCode.UNDEFINED)

        // Soft-reference flyweight cache: entries are eligible for GC under memory pressure,
        // preventing long-running JVMs from accumulating unbounded entries for transient artists.
        private val artistReferenceQueue = ReferenceQueue<Artist>()
        private val artistMap: ConcurrentHashMap<String, SoftReference<Artist>> = ConcurrentHashMap()

        /**
         * Returns a cached [Artist] instance for the given [name] and [countryCode].
         *
         * Uses a flyweight pattern over soft references so that repeated lookups of the same
         * artist return the same object instance. Returns [UNKNOWN] when both arguments are
         * at their default values.
         */
        @JvmStatic
        @JvmOverloads
        fun of(name: String, countryCode: CountryCode = CountryCode.UNDEFINED): Artist {
            val normalizedName = name.trim()
            if (normalizedName.isEmpty() && countryCode == CountryCode.UNDEFINED) return UNKNOWN
            expungeStaleEntries(artistMap, artistReferenceQueue)
            val key = id(normalizedName, countryCode)
            while (true) {
                val existing = artistMap[key]?.get()
                if (existing != null) return existing
                val fresh = Artist(normalizedName, countryCode)
                val ref = SoftReference(fresh, artistReferenceQueue)
                val prev = artistMap.putIfAbsent(key, ref) ?: return fresh
                val prevValue = prev.get()
                if (prevValue != null) return prevValue
                artistMap.remove(key, prev)
            }
        }

        internal fun id(name: String, countryCode: CountryCode = CountryCode.UNDEFINED) =
            if (countryCode == CountryCode.UNDEFINED) name else "$name-${countryCode.name}"
    }
}

/**
 * Returns a string key uniquely identifying this artist by name and country code.
 *
 * Used as the unique cache key for the flyweight map and as [net.transgressoft.lirp.entity.ReactiveEntityBase.uniqueId]
 * in artist catalog implementations.
 */
fun Artist.id(): String = if (countryCode == CountryCode.UNDEFINED) name else "$name-${countryCode.name}"