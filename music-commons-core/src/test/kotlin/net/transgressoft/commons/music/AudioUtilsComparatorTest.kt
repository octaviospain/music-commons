package net.transgressoft.commons.music

import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.audio.AudioItem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

internal class AudioUtilsComparatorTest : StringSpec({

    "Sorts items with null disc and track numbers" {
        val comparator = AudioUtils.audioItemTrackDiscNumberComparator<AudioItem>()

        // Test both disc numbers null
        val item1 =
            mockk<AudioItem> {
                every { discNumber } returns null
                every { trackNumber } returns null
            }
        val item2 =
            mockk<AudioItem> {
                every { discNumber } returns null
                every { trackNumber } returns null
            }
        comparator.compare(item1, item2) shouldBe 0

        // Test first disc number null
        val item3 =
            mockk<AudioItem> {
                every { discNumber } returns null
                every { trackNumber } returns 1
            }
        val item4 =
            mockk<AudioItem> {
                every { discNumber } returns 1
                every { trackNumber } returns 1
            }
        comparator.compare(item3, item4) shouldBeGreaterThan 0

        // Test second disc number null
        comparator.compare(item4, item3) shouldBeLessThan 0

        // Test disc numbers equal, both track numbers null
        val item5 =
            mockk<AudioItem> {
                every { discNumber } returns 1
                every { trackNumber } returns null
            }
        val item6 =
            mockk<AudioItem> {
                every { discNumber } returns 1
                every { trackNumber } returns null
            }
        comparator.compare(item5, item6) shouldBe 0

        // Test disc numbers equal, first track number null
        val item7 =
            mockk<AudioItem> {
                every { discNumber } returns 1
                every { trackNumber } returns null
            }
        val item8 =
            mockk<AudioItem> {
                every { discNumber } returns 1
                every { trackNumber } returns 5
            }
        comparator.compare(item7, item8) shouldBeGreaterThan 0

        // Test disc numbers equal, second track number null
        comparator.compare(item8, item7) shouldBeLessThan 0

        // Test normal comparison
        val item9 =
            mockk<AudioItem> {
                every { discNumber } returns 1
                every { trackNumber } returns 3
            }
        val item10 =
            mockk<AudioItem> {
                every { discNumber } returns 1
                every { trackNumber } returns 5
            }
        comparator.compare(item9, item10) shouldBeLessThan 0
        comparator.compare(item10, item9) shouldBeGreaterThan 0
    }
})