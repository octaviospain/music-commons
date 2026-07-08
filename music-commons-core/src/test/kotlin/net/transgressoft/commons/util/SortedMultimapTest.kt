package net.transgressoft.commons.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Verifies the sequential snapshot semantics of [SortedMultimap]. Its concurrent contract —
 * that reads stay linearizable while writers mutate — is verified exhaustively by
 * [SortedMultimapLinearizabilityTest] under Lincheck model checking.
 */
internal class SortedMultimapTest : StringSpec({

    "SortedMultimap get returns a stable snapshot" {
        val multimap = SortedMultimap<Int, Int>()
        multimap.put(1, 1)
        multimap.put(1, 2)

        val values = multimap[1]
        multimap.put(1, 3)

        values shouldBe setOf(1, 2)
    }

    "SortedMultimap putAll reports changed only when at least one value is new" {
        val multimap = SortedMultimap<Int, Int>()
        multimap.put(1, 1)

        multimap.putAll(1, listOf(1)) shouldBe false
        multimap.putAll(1, listOf(1, 2)) shouldBe true
        multimap.putAll(1, listOf(1, 2)) shouldBe false
    }

    "SortedMultimap remove and removeAll behave correctly" {
        val multimap = SortedMultimap<Int, Int>()
        multimap.put(1, 1)
        multimap.put(1, 2)
        multimap.put(2, 3)

        multimap.remove(99, 99) shouldBe false
        multimap.remove(1, 1) shouldBe true
        multimap[1] shouldBe setOf(2)
        multimap.removeAll(1) shouldBe setOf(2)
        multimap[1] shouldBe emptySet()
        multimap.removeAll(99) shouldBe emptySet()
        multimap.containsValue(3) shouldBe true
        multimap.containsValue(999) shouldBe false
    }
})