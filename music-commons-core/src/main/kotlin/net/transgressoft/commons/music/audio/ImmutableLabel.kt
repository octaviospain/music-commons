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
 * Immutable [Label] implementation using the flyweight pattern for efficient memory usage.
 *
 * Label instances are cached and reused based on their name and country code combination,
 * similar to [ImmutableArtist], to reduce memory consumption when managing audio libraries
 * with multiple tracks from the same record labels.
 */
@Serializable
@SerialName("ImmutableLabel")
class ImmutableLabel private constructor(override val name: String, override val countryCode: CountryCode = CountryCode.UNDEFINED) : Label {

    override fun compareTo(other: Label): Int {
        val result = compareValues(name, other.name)
        return if (result == 0)
            compareValues(countryCode, other.countryCode)
        else
            result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutableLabel

        if (name != other.name) return false
        if (countryCode != other.countryCode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + countryCode.hashCode()
        return result
    }

    companion object {
        @JvmField
        @get:JvmName("UNKNOWN")
        val UNKNOWN: Label = UnknownLabel

        // Soft-reference flyweight cache (see ImmutableArtist for design rationale).
        private val labelReferenceQueue = ReferenceQueue<Label>()
        private val labelMap: ConcurrentHashMap<String, SoftReference<Label>> = ConcurrentHashMap()

        @JvmStatic
        @JvmOverloads
        fun of(name: String, countryCode: CountryCode = CountryCode.UNDEFINED): Label {
            val normalizedName = name.trim()
            if (normalizedName.isEmpty() && countryCode == CountryCode.UNDEFINED) return UNKNOWN
            expungeStaleEntries(labelMap, labelReferenceQueue)
            val key = id(normalizedName, countryCode)
            while (true) {
                val existing = labelMap[key]?.get()
                if (existing != null) return existing
                val fresh = ImmutableLabel(name, countryCode)
                val ref = SoftReference(fresh as Label, labelReferenceQueue)
                val prev = labelMap.putIfAbsent(key, ref) ?: return fresh
                val prevValue = prev.get()
                if (prevValue != null) return prevValue
                labelMap.remove(key, prev)
            }
        }

        internal fun id(name: String, countryCode: CountryCode = CountryCode.UNDEFINED) =
            if (countryCode == CountryCode.UNDEFINED) {
                name
            } else {
                "$name (${countryCode.name})"
            }
    }
}