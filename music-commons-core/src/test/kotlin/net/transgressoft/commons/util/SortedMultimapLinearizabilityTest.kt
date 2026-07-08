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

import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.lincheck.datastructures.IntGen
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.Param
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated

/*
 * Lincheck model check of [SortedMultimap]'s concurrent contract. SortedMultimap is the deterministic
 * concurrent structure in this module (coarse @Synchronized over TreeMap/TreeSet, no logging,
 * reflection, SoftReference, IO or coroutines), so it is model-checkable — unlike the process-wide
 * RegistryBase / LirpContext registry, whose non-deterministic internals defeat Lincheck's replay.
 *
 * Lincheck exhaustively explores put/remove/get/containsValue interleavings across two threads and
 * verifies each outcome is linearizable against a sequential specification — replacing hand-rolled
 * thread + CountDownLatch stress with exhaustive coverage. A plain JUnit 5 class, @Isolated so it
 * does not run alongside other registry/JavaFX specs in the same JVM.
 */
@Isolated
@Param(name = "key", gen = IntGen::class, conf = "1:3")
@Param(name = "value", gen = IntGen::class, conf = "1:3")
@DisplayName("SortedMultimap linearizability")
internal class SortedMultimapLinearizabilityTest {

    private val multimap = SortedMultimap<Int, Int>()

    @Operation
    fun put(
        @Param(name = "key") key: Int,
        @Param(name = "value") value: Int
    ): Boolean = multimap.put(key, value)

    @Operation
    fun remove(
        @Param(name = "key") key: Int,
        @Param(name = "value") value: Int
    ): Boolean = multimap.remove(key, value)

    @Operation
    fun get(
        @Param(name = "key") key: Int
    ): Set<Int> = multimap[key]

    @Operation
    fun containsValue(
        @Param(name = "value") value: Int
    ): Boolean = multimap.containsValue(value)

    @Operation
    fun entries(): Set<Map.Entry<Int, Int>> = multimap.entries()

    @Test
    @DisplayName("concurrent put/remove/get/containsValue on SortedMultimap are linearizable")
    fun modelCheck() {
        ModelCheckingOptions()
            .iterations(50)
            .invocationsPerIteration(500)
            .threads(2)
            .actorsPerThread(3)
            .check(this::class)
    }
}