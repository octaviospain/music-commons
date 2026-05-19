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

package net.transgressoft.commons.util

import java.util.AbstractMap
import java.util.TreeMap
import java.util.TreeSet

/**
 * Small sorted multimap for internal relationships that need deterministic key and value traversal.
 */
internal class SortedMultimap<K : Comparable<K>, V : Comparable<V>> {

    private val valuesByKey = TreeMap<K, TreeSet<V>>()

    @Synchronized
    operator fun get(key: K): Set<V> = valuesByKey[key]?.toCollection(LinkedHashSet()) ?: emptySet()

    @Synchronized
    fun put(
        key: K,
        value: V
    ): Boolean = valuesByKey.getOrPut(key) { TreeSet() }.add(value)

    @Synchronized
    fun putAll(
        key: K,
        values: Iterable<V>
    ): Boolean {
        var changed = false
        values.forEach { value ->
            changed = put(key, value) || changed
        }
        return changed
    }

    @Synchronized
    fun remove(
        key: K,
        value: V
    ): Boolean {
        val values = valuesByKey[key] ?: return false
        return values.remove(value).also {
            if (values.isEmpty()) {
                valuesByKey.remove(key)
            }
        }
    }

    @Synchronized
    fun removeAll(key: K): Set<V> = valuesByKey.remove(key)?.toCollection(LinkedHashSet()) ?: emptySet()

    @Synchronized
    fun containsValue(value: Any?): Boolean = valuesByKey.values.any { value in it }

    @Synchronized
    fun entries(): Set<Map.Entry<K, V>> =
        valuesByKey.flatMapTo(LinkedHashSet()) { (key, values) ->
            values.map { value -> AbstractMap.SimpleImmutableEntry(key, value) }
        }
}