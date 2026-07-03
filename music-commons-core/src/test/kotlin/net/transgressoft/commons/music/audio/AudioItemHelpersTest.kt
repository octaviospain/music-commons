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

    "albumBucketComparator sorts two buckets with distinct names case-insensitively ascending" {
        val abbeyRoad = album(AlbumDetails("abbey road", Artist.of("The Beatles")))
        val nevermind = album(AlbumDetails("Nevermind", Artist.of("Nirvana")))

        comparator.compare(abbeyRoad, nevermind) shouldBeLessThan 0
        comparator.compare(nevermind, abbeyRoad) shouldBeGreaterThan 0
    }

    "albumBucketComparator tie-breaks on album artist name when album names are equal after normalization" {
        val beatles = album(AlbumDetails("White Album", Artist.of("The Beatles")))
        val nirvana = album(AlbumDetails("White Album", Artist.of("Nirvana")))

        // "Nirvana" < "The Beatles" alphabetically
        comparator.compare(nirvana, beatles) shouldBeLessThan 0
        comparator.compare(beatles, nirvana) shouldBeGreaterThan 0
    }

    "albumBucketComparator tie-breaks on year ascending when name and artist are equal" {
        val earlier = album(AlbumDetails("OK Computer", Artist.of("Radiohead"), year = 1997))
        val later = album(AlbumDetails("OK Computer", Artist.of("Radiohead"), year = 2000))

        comparator.compare(earlier, later) shouldBeLessThan 0
        comparator.compare(later, earlier) shouldBeGreaterThan 0
    }

    "albumBucketComparator sorts null year after non-null year" {
        val withYear = album(AlbumDetails("Kid A", Artist.of("Radiohead"), year = 2000))
        val withoutYear = album(AlbumDetails("Kid A", Artist.of("Radiohead"), year = null))

        comparator.compare(withYear, withoutYear) shouldBeLessThan 0
        comparator.compare(withoutYear, withYear) shouldBeGreaterThan 0
    }

    "albumBucketComparator sorts bucket with blank album name after all named buckets" {
        val named = album(AlbumDetails("Abbey Road", Artist.of("The Beatles")))
        val blank = album(AlbumDetails("", Artist.of("Unknown")))

        comparator.compare(named, blank) shouldBeLessThan 0
        comparator.compare(blank, named) shouldBeGreaterThan 0
    }

    "albumBucketComparator returns zero for two buckets both with blank album names" {
        val blank1 = album(AlbumDetails("", Artist.of("Artist A")))
        val blank2 = album(AlbumDetails("", Artist.of("Artist B")))

        comparator.compare(blank1, blank2) shouldBe 0
        comparator.compare(blank2, blank1) shouldBe 0
    }
})