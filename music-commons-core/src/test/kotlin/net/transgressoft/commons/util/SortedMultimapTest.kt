package net.transgressoft.commons.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Verifies the snapshot semantics expected by concurrent hierarchy readers.
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

    "SortedMultimap entries can be read while writes happen concurrently" {
        val multimap = SortedMultimap<Int, Int>()
        repeat(100) { multimap.put(0, it) }
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()

        val reader =
            thread {
                start.await()
                repeat(5_000) {
                    runCatching {
                        multimap.entries().forEach { entry -> entry.key + entry.value }
                    }.onFailure { failure.compareAndSet(null, it) }
                }
            }

        val writer =
            thread {
                start.await()
                repeat(5_000) {
                    val value = it % 200
                    multimap.put(it % 10, value)
                    multimap.remove((it + 1) % 10, value)
                }
            }

        start.countDown()
        reader.join()
        writer.join()

        failure.get() shouldBe null
    }
})