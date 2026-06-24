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
internal class ImmutableAlbumCatalogTest : StringSpec({

    val files = virtualFiles()

    fun createAudioItem(path: Path): AudioItem =
        MutableAudioItemTestBridge.createAudioItem(path, files.metadataIO)

    fun createAudioItem(path: Path, id: Int): AudioItem =
        MutableAudioItemTestBridge.createAudioItem(path, id, files.metadataIO)

    "ImmutableAlbumCatalog exposes album, size, isEmpty, and audioItems from construction list" {
        val artist = Arb.artist().next()
        val album = Arb.album(albumArtist = artist).next()
        val audioItem =
            Arb.audioItem {
                this.album = album
                trackNumber = 1
            }.next()

        val catalog = ImmutableAlbumCatalog(album, listOf(audioItem))

        catalog.album shouldBe album
        catalog.size shouldBe 1
        catalog.isEmpty shouldBe false
        catalog.audioItems.shouldContainOnly(audioItem)
    }

    "ImmutableAlbumCatalog isEmpty is true for empty list" {
        val album = Arb.album().next()

        val catalog = ImmutableAlbumCatalog(album, emptyList())

        catalog.isEmpty shouldBe true
        catalog.size shouldBe 0
        catalog.audioItems shouldBe emptySet()
    }

    "ImmutableAlbumCatalog deduplicates items with the same assigned id" {
        val artist = Arb.artist().next()
        val album = Album("Dedup Album", artist)
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val item1 = createAudioItem(path, 1)
        val item2 = createAudioItem(path, 1)

        val catalog = ImmutableAlbumCatalog(album, listOf(item1, item2))

        catalog.size shouldBe 1
    }

    "ImmutableAlbumCatalog does not deduplicate items with distinct assigned ids" {
        val artist = Arb.artist().next()
        val album = Album("Distinct Album", artist)
        val path1 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val path2 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val item1 = createAudioItem(path1, 1)
        val item2 = createAudioItem(path2, 2)

        val catalog = ImmutableAlbumCatalog(album, listOf(item1, item2))

        catalog.size shouldBe 2
        catalog.audioItems.shouldContainOnly(item1, item2)
    }

    "ImmutableAlbumCatalog deduplicates by uniqueId when both items have UNASSIGNED_ID" {
        val audioItem = Arb.audioItem().next()

        val catalog = ImmutableAlbumCatalog(audioItem.album, listOf(audioItem, audioItem))

        catalog.size shouldBe 1
    }

    "ImmutableAlbumCatalog audioItems are in natural order" {
        val artist = Arb.artist().next()
        val album = Album("Natural Order", artist)
        val paths = files.virtualAlbumAudioFiles(artist, album, size = 3..5).next()
        val items = paths.mapIndexed { idx, path -> createAudioItem(path, idx + 1) }

        val catalog = ImmutableAlbumCatalog(album, items)

        val sortedItems = items.sorted()
        catalog.audioItems.toList() shouldBe sortedItems
    }

    "ImmutableAlbumCatalog compareTo orders by album" {
        val artist = Arb.artist().next()
        val albumA = Album("A Album", artist)
        val albumZ = Album("Z Album", artist)
        val itemA = Arb.audioItem { album = albumA }.next()
        val itemZ = Arb.audioItem { album = albumZ }.next()

        val catalogA = ImmutableAlbumCatalog(albumA, listOf(itemA))
        val catalogZ = ImmutableAlbumCatalog(albumZ, listOf(itemZ))

        (catalogA.compareTo(catalogZ) < 0) shouldBe true
        (catalogZ.compareTo(catalogA) > 0) shouldBe true
        (catalogA.compareTo(catalogA) == 0) shouldBe true
    }

    "ImmutableAlbumCatalog equals is true for same album and same items" {
        val album = Arb.album().next()
        val audioItem = Arb.audioItem { this.album = album }.next()

        val catalog1 = ImmutableAlbumCatalog(album, listOf(audioItem))
        val catalog2 = ImmutableAlbumCatalog(album, listOf(audioItem))

        (catalog1 == catalog2) shouldBe true
        catalog1.hashCode() shouldBe catalog2.hashCode()
    }

    "ImmutableAlbumCatalog equals is false for different albums" {
        val artist = Arb.artist().next()
        val album1 = Album("First Album", artist)
        val album2 = Album("Second Album", artist)
        val item1 = Arb.audioItem { album = album1 }.next()
        val item2 = Arb.audioItem { album = album2 }.next()

        val catalog1 = ImmutableAlbumCatalog(album1, listOf(item1))
        val catalog2 = ImmutableAlbumCatalog(album2, listOf(item2))

        (catalog1 == catalog2) shouldBe false
    }

    "ImmutableAlbumCatalog equals returns false for null and non-catalog types" {
        val album = Arb.album().next()
        val catalog = ImmutableAlbumCatalog(album, listOf(Arb.audioItem { this.album = album }.next()))

        catalog.equals(null) shouldBe false
        catalog.equals("not a catalog") shouldBe false
    }

    "ImmutableAlbumCatalog hashCode differs when albums differ" {
        val artist = Arb.artist().next()
        val album1 = Album("Hash Album 1", artist)
        val album2 = Album("Hash Album 2", artist)
        val item1 = Arb.audioItem { album = album1 }.next()
        val item2 = Arb.audioItem { album = album2 }.next()

        val catalog1 = ImmutableAlbumCatalog(album1, listOf(item1))
        val catalog2 = ImmutableAlbumCatalog(album2, listOf(item2))

        catalog1.hashCode() shouldNotBe catalog2.hashCode()
    }

    "ImmutableAlbumCatalog toString includes album and size" {
        val artist = Artist.of("Test Artist")
        val album = Album("Test Album", artist)
        val audioItem = Arb.audioItem { this.album = album }.next()

        val catalog = ImmutableAlbumCatalog(album, listOf(audioItem))

        catalog.toString() shouldBe "ImmutableAlbumCatalog(album=$album, size=1)"
    }

    "ImmutableAlbumCatalog clone returns itself" {
        val album = Arb.album().next()
        val catalog = ImmutableAlbumCatalog(album, listOf(Arb.audioItem { this.album = album }.next()))

        (catalog.clone() === catalog) shouldBe true
    }

    "ImmutableAlbumCatalog coverImageBytes returns cover of first covered item" {
        val album = Arb.album().next()
        val expectedCover = byteArrayOf(10, 20, 30)
        val itemNoCover =
            Arb.audioItem {
                this.album = album
                trackNumber = 1
                discNumber = 1
                coverImageBytes = null
            }.next()
        val itemWithCover =
            Arb.audioItem {
                this.album = album
                trackNumber = 2
                discNumber = 1
                coverImageBytes = expectedCover
            }.next()

        val catalog = ImmutableAlbumCatalog(album, listOf(itemNoCover, itemWithCover))

        catalog.coverImageBytes shouldBe expectedCover
    }

    "ImmutableAlbumCatalog coverImageBytes returns null when no item has a cover" {
        val album = Arb.album().next()
        val item1 =
            Arb.audioItem {
                this.album = album
                trackNumber = 1
                coverImageBytes = null
            }.next()
        val item2 =
            Arb.audioItem {
                this.album = album
                trackNumber = 2
                coverImageBytes = null
            }.next()

        val catalog = ImmutableAlbumCatalog(album, listOf(item1, item2))

        catalog.coverImageBytes shouldBe null
    }

    "ImmutableAlbumCatalog retains two distinct UNASSIGNED_ID items with same disc and track" {
        val artist = Arb.artist().next()
        val album = Album("Identity Album", artist)
        val path1 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val path2 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val item1 = createAudioItem(path1)
        val item2 = createAudioItem(path2)

        val catalog = ImmutableAlbumCatalog(album, listOf(item1, item2))

        catalog.size shouldBe 2
        catalog.audioItems.shouldContainOnly(item1, item2)
    }
})