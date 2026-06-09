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
import net.transgressoft.lirp.persistence.Embeddable
import net.transgressoft.lirp.persistence.PersistenceCreator
import net.transgressoft.lirp.persistence.PersistenceProperty
import com.neovisionaries.i18n.CountryCode
import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Concrete value type representing a record label with an optional country of origin.
 *
 * Uses a flyweight pattern over soft references so that identical labels share the same
 * object instance under normal memory conditions, similar to [Artist]. The [UNKNOWN] singleton
 * represents a label whose name and country are unspecified.
 *
 * Access instances via the [of] factory rather than the constructor directly.
 */
@ConsistentCopyVisibility
@Embeddable
data class Label internal constructor(
    val name: String,
    @PersistenceProperty(converter = CountryConverter::class) val countryCode: CountryCode = CountryCode.UNDEFINED
) : Comparable<Label> {

    override fun compareTo(other: Label): Int {
        val result = compareValues(name, other.name)
        return if (result == 0) compareValues(countryCode, other.countryCode) else result
    }

    companion object {

        @JvmField
        @get:JvmName("UNKNOWN")
        val UNKNOWN: Label = Label("", CountryCode.UNDEFINED)

        // Soft-reference flyweight cache (see Artist for design rationale).
        private val labelReferenceQueue = ReferenceQueue<Label>()
        private val labelMap: ConcurrentHashMap<String, SoftReference<Label>> = ConcurrentHashMap()

        /**
         * Returns a cached [Label] instance for the given [name] and [countryCode].
         *
         * Uses a flyweight pattern over soft references so that repeated lookups of the same
         * label return the same object instance. Returns [UNKNOWN] when both arguments are at
         * their default values.
         */
        @JvmStatic
        @JvmOverloads
        @PersistenceCreator
        fun of(name: String, countryCode: CountryCode = CountryCode.UNDEFINED): Label {
            val normalizedName = name.trim()
            if (normalizedName.isEmpty() && countryCode == CountryCode.UNDEFINED) return UNKNOWN
            expungeStaleEntries(labelMap, labelReferenceQueue)
            val key = id(normalizedName, countryCode)
            while (true) {
                val existing = labelMap[key]?.get()
                if (existing != null) return existing
                val fresh = Label(normalizedName, countryCode)
                val ref = SoftReference(fresh, labelReferenceQueue)
                val prev = labelMap.putIfAbsent(key, ref) ?: return fresh
                val prevValue = prev.get()
                if (prevValue != null) return prevValue
                labelMap.remove(key, prev)
            }
        }

        // Uses same cache key format as ImmutableLabel for consistency with prior persisted data.
        internal fun id(name: String, countryCode: CountryCode = CountryCode.UNDEFINED) =
            if (countryCode == CountryCode.UNDEFINED) {
                name
            } else {
                "$name (${countryCode.name})"
            }
    }
}