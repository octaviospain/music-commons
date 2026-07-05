package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.audioItemTrackDiscNumberComparator
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.math.sign

internal class AudioItemTrackDiscNumberComparatorTest : StringSpec({

    val comparator = audioItemTrackDiscNumberComparator<AudioItem>()

    fun item(disc: Short?, track: Short?): AudioItem =
        mockk {
            every { discNumber } returns disc
            every { trackNumber } returns track
        }

    data class Case(
        val label: String,
        val disc1: Short?,
        val track1: Short?,
        val disc2: Short?,
        val track2: Short?,
        val expectedSign: Int
    )

    withData(
        nameFn = { it.label },
        Case("both disc numbers null", null, null, null, null, 0),
        Case("first disc number null orders after", null, 1, 1, 1, 1),
        Case("second disc number null orders before", 1, 1, null, 1, -1),
        Case("disc equal and both track numbers null", 1, null, 1, null, 0),
        Case("disc equal and first track number null orders after", 1, null, 1, 5, 1),
        Case("disc equal and second track number null orders before", 1, 5, 1, null, -1),
        Case("disc equal and normal track ordering ascending", 1, 3, 1, 5, -1),
        Case("disc equal and normal track ordering descending", 1, 5, 1, 3, 1),
        Case("disc differs and track equal orders by disc ascending", 1, 1, 2, 1, -1),
        Case("disc differs and track equal orders by disc descending", 2, 1, 1, 1, 1)
    ) { (_, disc1, track1, disc2, track2, expectedSign) ->
        val a = item(disc1, track1)
        val b = item(disc2, track2)

        comparator.compare(a, b).sign shouldBe expectedSign
    }
})