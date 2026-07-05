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

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.nio.file.Path
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class ImmutableGenreIndexTest : StringSpec({

    val files = virtualFiles()

    fun createAudioItem(path: Path): AudioItem =
        MutableAudioItemTestBridge.createAudioItem(path, files.metadataIO)

    fun createAudioItem(path: Path, id: Int): AudioItem =
        MutableAudioItemTestBridge.createAudioItem(path, id, files.metadataIO)

    "ImmutableGenreIndex exposes genre, size, isEmpty, and tracks from construction list" {
        val genre = Rock
        val audioItem = Arb.audioItem { genres = setOf(genre) }.next()

        val index = ImmutableGenreIndex(genre, listOf(audioItem))

        index.genre shouldBe genre
        index.size shouldBe 1
        index.isEmpty shouldBe false
        index.tracks.shouldContainOnly(audioItem)
    }

    "ImmutableGenreIndex isEmpty is true for empty list" {
        val index = ImmutableGenreIndex(Jazz, emptyList())

        index.isEmpty shouldBe true
        index.size shouldBe 0
        index.tracks shouldBe emptyList()
    }

    "ImmutableGenreIndex preserves all items as passed — deduplication is the projection layer's responsibility" {
        val genre = Blues
        val artist = Arb.artist().next()
        val album = AlbumDetails("Crossroads Album", artist)
        val path1 =
            files.virtualAudioFile {
                genres = setOf(genre)
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val path2 =
            files.virtualAudioFile {
                genres = setOf(genre)
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val item1 = createAudioItem(path1, 1)
        val item2 = createAudioItem(path2, 2)

        val index = ImmutableGenreIndex(genre, listOf(item1, item2))

        index.size shouldBe 2
        index.tracks.shouldContainOnly(item1, item2)
    }

    "ImmutableGenreIndex preserves the construction order — ordering is the projection layer's responsibility" {
        val genre = Electronic
        val artistA = Artist.of("A Artist")
        val artistZ = Artist.of("Z Artist")
        val albumA = AlbumDetails("A Album", artistA)
        val albumZ = AlbumDetails("Z Album", artistZ)
        val itemArtistA =
            Arb.audioItem {
                artist = artistA
                this.album = albumA
                genres = setOf(genre)
                trackNumber = 1
                discNumber = 1
            }.next()
        val itemArtistZ =
            Arb.audioItem {
                artist = artistZ
                this.album = albumZ
                genres = setOf(genre)
                trackNumber = 1
                discNumber = 1
            }.next()

        val index = ImmutableGenreIndex(genre, listOf(itemArtistA, itemArtistZ))

        index.tracks shouldBe listOf(itemArtistA, itemArtistZ)
    }

    "ImmutableGenreIndex compareTo orders by genre name" {
        val genreA = Genre.Custom("Ambient")
        val genreZ = Genre.Custom("Zydeco")
        val itemA = Arb.audioItem { genres = setOf(genreA) }.next()
        val itemZ = Arb.audioItem { genres = setOf(genreZ) }.next()

        val indexA = ImmutableGenreIndex(genreA, listOf(itemA))
        val indexZ = ImmutableGenreIndex(genreZ, listOf(itemZ))

        indexA shouldBeLessThan indexZ
        indexZ shouldBeGreaterThan indexA
        indexA shouldBeEqualComparingTo indexA
    }

    "ImmutableGenreIndex equals is true for same genre and same items" {
        val genre = Folk
        val audioItem = Arb.audioItem { genres = setOf(genre) }.next()

        val index1 = ImmutableGenreIndex(genre, listOf(audioItem))
        val index2 = ImmutableGenreIndex(genre, listOf(audioItem))

        (index1 == index2) shouldBe true
        index1.hashCode() shouldBe index2.hashCode()
    }

    "ImmutableGenreIndex with different genres is unequal in both equals and hashCode" {
        val item1 = Arb.audioItem { genres = setOf(Rock) }.next()
        val item2 = Arb.audioItem { genres = setOf(Jazz) }.next()

        val index1 = ImmutableGenreIndex(Rock, listOf(item1))
        val index2 = ImmutableGenreIndex(Jazz, listOf(item2))

        assertSoftly {
            (index1 == index2) shouldBe false
            index1.hashCode() shouldNotBe index2.hashCode()
        }
    }

    "ImmutableGenreIndex equals returns false for null and non-index types" {
        val index = ImmutableGenreIndex(Pop, listOf(Arb.audioItem { genres = setOf(Pop) }.next()))

        index.equals(null) shouldBe false
        index.equals("not an index") shouldBe false
    }

    "ImmutableGenreIndex toString includes genre and size" {
        val genre = Rock
        val audioItem = Arb.audioItem { genres = setOf(genre) }.next()

        val index = ImmutableGenreIndex(genre, listOf(audioItem))

        index.toString() shouldBe "ImmutableGenreIndex(genre=$genre, size=1)"
    }

    "ImmutableGenreIndex clone returns itself" {
        val genre = Alternative
        val index = ImmutableGenreIndex(genre, listOf(Arb.audioItem { genres = setOf(genre) }.next()))

        (index.clone() === index) shouldBe true
    }

    "ImmutableGenreIndex retains two distinct UNASSIGNED_ID items with same artist album and track" {
        val genre = Rock
        val artist = Artist.of("Same Artist")
        val album = AlbumDetails("Same Album", artist)
        val path1 =
            files.virtualAudioFile {
                genres = setOf(genre)
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val path2 =
            files.virtualAudioFile {
                genres = setOf(genre)
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val item1 = createAudioItem(path1)
        val item2 = createAudioItem(path2)

        val index = ImmutableGenreIndex(genre, listOf(item1, item2))

        index.size shouldBe 2
        index.tracks.shouldContainOnly(item1, item2)
    }
})