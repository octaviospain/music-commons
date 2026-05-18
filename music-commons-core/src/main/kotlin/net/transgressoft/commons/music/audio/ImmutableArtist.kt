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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Immutable [Artist] implementation using the flyweight pattern for efficient memory usage.
 *
 * Artist instances are cached and reused based on their name and country code combination,
 * ensuring that identical artists share the same object instance. This design reduces memory
 * footprint when managing large audio libraries with many tracks by the same artists.
 */
@Serializable
@SerialName("ImmutableArtist")
class ImmutableArtist private constructor(override val name: String, override val countryCode: CountryCode = CountryCode.UNDEFINED) : Artist {

    internal val id = id(name, countryCode)

    override fun compareTo(other: Artist): Int {
        val result = compareValues(name, other.name)
        return if (result == 0)
            compareValues(countryCode, other.countryCode)
        else
            result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutableArtist

        if (name != other.name) return false
        if (countryCode != other.countryCode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + countryCode.hashCode()
        return result
    }

    override fun toString() = "Artist(name=$name, countryCode=${countryCode.name})"

    companion object {

        @JvmField
        @get:JvmName("UNKNOWN")
        val UNKNOWN: Artist = UnknownArtist

        // Soft-reference flyweight cache: entries are eligible for GC under memory pressure, which
        // prevents long-running JVMs (test suites, library consumers) from accumulating unbounded
        // entries for transient artists. The UNKNOWN sentinel is held via a strong reference to
        // guarantee identity stability for the public API.
        private val artistReferenceQueue = ReferenceQueue<Artist>()
        private val artistMap: ConcurrentHashMap<String, SoftReference<Artist>> = ConcurrentHashMap()

        /**
         * Returns a cached [Artist] instance for the given name and country code.
         *
         * Uses a flyweight pattern over soft references so cached instances can be reclaimed under
         * memory pressure. Under normal load, repeated lookups of the same artist return the same
         * object instance.
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
                val fresh = ImmutableArtist(normalizedName, countryCode)
                val ref = SoftReference(fresh as Artist, artistReferenceQueue)
                val prev = artistMap.putIfAbsent(key, ref) ?: return fresh
                val prevValue = prev.get()
                if (prevValue != null) return prevValue
                artistMap.remove(key, prev)
            }
        }

        internal fun id(name: String, countryCode: CountryCode = CountryCode.UNDEFINED) =
            if (countryCode == CountryCode.UNDEFINED) {
                name
            } else {
                "$name-${countryCode.name}"
            }
    }
}

fun Artist.id() = ImmutableArtist.id(name, countryCode)