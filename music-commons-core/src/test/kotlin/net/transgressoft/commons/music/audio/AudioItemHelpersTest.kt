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

import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

internal class AudioItemHelpersTest : StringSpec({

    fun album(albumDetails: AlbumDetails): Album<AudioItem> =
        mockk<Album<AudioItem>> {
            every { album } returns albumDetails
        }

    val comparator = albumBucketComparator<Album<AudioItem>>()

    data class OrderingCase(val label: String, val smaller: Album<AudioItem>, val greater: Album<AudioItem>)

    withData(
        nameFn = { it.label },
        OrderingCase(
            "albumBucketComparator sorts two buckets with distinct names case-insensitively ascending",
            album(AlbumDetails("abbey road", Artist.of("The Beatles"))),
            album(AlbumDetails("Nevermind", Artist.of("Nirvana")))
        ),
        OrderingCase(
            "albumBucketComparator tie-breaks on album artist name when album names are equal after normalization",
            album(AlbumDetails("White Album", Artist.of("Nirvana"))),
            album(AlbumDetails("White Album", Artist.of("The Beatles")))
        ),
        OrderingCase(
            "albumBucketComparator tie-breaks on year ascending when name and artist are equal",
            album(AlbumDetails("OK Computer", Artist.of("Radiohead"), year = 1997)),
            album(AlbumDetails("OK Computer", Artist.of("Radiohead"), year = 2000))
        ),
        OrderingCase(
            "albumBucketComparator sorts null year after non-null year",
            album(AlbumDetails("Kid A", Artist.of("Radiohead"), year = 2000)),
            album(AlbumDetails("Kid A", Artist.of("Radiohead"), year = null))
        ),
        OrderingCase(
            "albumBucketComparator sorts bucket with blank album name after all named buckets",
            album(AlbumDetails("Abbey Road", Artist.of("The Beatles"))),
            album(AlbumDetails("", Artist.of("Unknown")))
        )
    ) { (_, smaller, greater) ->
        comparator.compare(smaller, greater) shouldBeLessThan 0
        comparator.compare(greater, smaller) shouldBeGreaterThan 0
    }

    "albumBucketComparator returns zero for two buckets both with blank album names" {
        val blank1 = album(AlbumDetails("", Artist.of("Artist A")))
        val blank2 = album(AlbumDetails("", Artist.of("Artist B")))

        comparator.compare(blank1, blank2) shouldBe 0
        comparator.compare(blank2, blank1) shouldBe 0
    }
})