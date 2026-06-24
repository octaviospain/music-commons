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
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.nio.file.Path
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class ImmutableGenreCatalogTest : StringSpec({

    val files = virtualFiles()

    fun createAudioItem(path: Path): AudioItem =
        MutableAudioItemTestBridge.createAudioItem(path, files.metadataIO)

    fun createAudioItem(path: Path, id: Int): AudioItem =
        MutableAudioItemTestBridge.createAudioItem(path, id, files.metadataIO)

    "ImmutableGenreCatalog exposes genre, size, isEmpty, and audioItems from construction list" {
        val genre = Rock
        val audioItem = Arb.audioItem { genres = setOf(genre) }.next()

        val catalog = ImmutableGenreCatalog(genre, listOf(audioItem))

        catalog.genre shouldBe genre
        catalog.size shouldBe 1
        catalog.isEmpty shouldBe false
        catalog.audioItems.shouldContainOnly(audioItem)
    }

    "ImmutableGenreCatalog isEmpty is true for empty list" {
        val catalog = ImmutableGenreCatalog(Jazz, emptyList())

        catalog.isEmpty shouldBe true
        catalog.size shouldBe 0
        catalog.audioItems shouldBe emptySet()
    }

    "ImmutableGenreCatalog deduplicates items with the same assigned id" {
        val genre = Blues
        val path =
            files.virtualAudioFile {
                genres = setOf(genre)
                title = "Crossroads"
                trackNumber = 1
                discNumber = 1
            }.next()
        val item1 = createAudioItem(path, 1)
        // Both items share an explicit assigned id to exercise the same-assigned-id dedup branch
        val item2 = createAudioItem(path, 1)

        val catalog = ImmutableGenreCatalog(genre, listOf(item1, item2))

        catalog.size shouldBe 1
    }

    "ImmutableGenreCatalog does not deduplicate items with distinct assigned ids" {
        val genre = Blues
        val artist = Arb.artist().next()
        val album = Album("Crossroads Album", artist)
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

        val catalog = ImmutableGenreCatalog(genre, listOf(item1, item2))

        catalog.size shouldBe 2
        catalog.audioItems.shouldContainOnly(item1, item2)
    }

    "ImmutableGenreCatalog deduplicates by uniqueId when both items have UNASSIGNED_ID" {
        val audioItem = Arb.audioItem { genres = setOf(Rock) }.next()

        val catalog = ImmutableGenreCatalog(Rock, listOf(audioItem, audioItem))

        catalog.size shouldBe 1
    }

    "ImmutableGenreCatalog audioItems are ordered by artist then album then track" {
        val genre = Electronic
        val artistA = Artist.of("A Artist")
        val artistZ = Artist.of("Z Artist")
        val albumA = Album("A Album", artistA)
        val albumZ = Album("Z Album", artistZ)
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

        val catalog = ImmutableGenreCatalog(genre, listOf(itemArtistZ, itemArtistA))

        catalog.audioItems.toList() shouldBe listOf(itemArtistA, itemArtistZ)
    }

    "ImmutableGenreCatalog compareTo orders by genre name" {
        val genreA = Genre.Custom("Ambient")
        val genreZ = Genre.Custom("Zydeco")
        val itemA = Arb.audioItem { genres = setOf(genreA) }.next()
        val itemZ = Arb.audioItem { genres = setOf(genreZ) }.next()

        val catalogA = ImmutableGenreCatalog(genreA, listOf(itemA))
        val catalogZ = ImmutableGenreCatalog(genreZ, listOf(itemZ))

        (catalogA.compareTo(catalogZ) < 0) shouldBe true
        (catalogZ.compareTo(catalogA) > 0) shouldBe true
        (catalogA.compareTo(catalogA) == 0) shouldBe true
    }

    "ImmutableGenreCatalog equals is true for same genre and same items" {
        val genre = Folk
        val audioItem = Arb.audioItem { genres = setOf(genre) }.next()

        val catalog1 = ImmutableGenreCatalog(genre, listOf(audioItem))
        val catalog2 = ImmutableGenreCatalog(genre, listOf(audioItem))

        (catalog1 == catalog2) shouldBe true
        catalog1.hashCode() shouldBe catalog2.hashCode()
    }

    "ImmutableGenreCatalog equals is false for different genres" {
        val item1 = Arb.audioItem { genres = setOf(Rock) }.next()
        val item2 = Arb.audioItem { genres = setOf(Jazz) }.next()

        val catalog1 = ImmutableGenreCatalog(Rock, listOf(item1))
        val catalog2 = ImmutableGenreCatalog(Jazz, listOf(item2))

        (catalog1 == catalog2) shouldBe false
    }

    "ImmutableGenreCatalog equals returns false for null and non-catalog types" {
        val catalog = ImmutableGenreCatalog(Pop, listOf(Arb.audioItem { genres = setOf(Pop) }.next()))

        catalog.equals(null) shouldBe false
        catalog.equals("not a catalog") shouldBe false
    }

    "ImmutableGenreCatalog hashCode differs when genres differ" {
        val item1 = Arb.audioItem { genres = setOf(Classical) }.next()
        val item2 = Arb.audioItem { genres = setOf(Metal) }.next()

        val catalog1 = ImmutableGenreCatalog(Classical, listOf(item1))
        val catalog2 = ImmutableGenreCatalog(Metal, listOf(item2))

        catalog1.hashCode() shouldNotBe catalog2.hashCode()
    }

    "ImmutableGenreCatalog toString includes genre and size" {
        val genre = Rock
        val audioItem = Arb.audioItem { genres = setOf(genre) }.next()

        val catalog = ImmutableGenreCatalog(genre, listOf(audioItem))

        catalog.toString() shouldBe "ImmutableGenreCatalog(genre=$genre, size=1)"
    }

    "ImmutableGenreCatalog clone returns itself" {
        val genre = Alternative
        val catalog = ImmutableGenreCatalog(genre, listOf(Arb.audioItem { genres = setOf(genre) }.next()))

        (catalog.clone() === catalog) shouldBe true
    }

    "ImmutableGenreCatalog retains two distinct UNASSIGNED_ID items with same artist album and track" {
        val genre = Rock
        val artist = Artist.of("Same Artist")
        val album = Album("Same Album", artist)
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

        val catalog = ImmutableGenreCatalog(genre, listOf(item1, item2))

        catalog.size shouldBe 2
        catalog.audioItems.shouldContainOnly(item1, item2)
    }
})