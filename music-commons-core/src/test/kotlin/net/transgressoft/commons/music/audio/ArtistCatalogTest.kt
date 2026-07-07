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

import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.nio.file.Path
import kotlinx.coroutines.ExperimentalCoroutinesApi

@DisplayName("ArtistCatalog")
@ExperimentalCoroutinesApi
internal class ArtistCatalogTest : StringSpec({

    val files = virtualFiles()

    fun createAudioItem(path: Path): AudioItem =
        MutableAudioItemTestBridge.createAudioItem(path, files.metadataIO)

    fun createAudioItem(path: Path, id: Int): AudioItem =
        MutableAudioItemTestBridge.createAudioItem(path, id, files.metadataIO)

    "ArtistCatalog builds correct album grouping from list" {
        val expectedArtist = Arb.artist().next()
        val expectedAlbum = Arb.album().next()
        val audioItem =
            Arb.audioItem {
                artist = expectedArtist
                album = expectedAlbum
                trackNumber = 1
            }.next()

        val catalog = ImmutableArtistCatalog(expectedArtist, listOf(audioItem))

        catalog.artist shouldBe expectedArtist
        catalog.size shouldBe 1
        catalog.albums.size shouldBe 1
        catalog.albums.first().albumName shouldBe expectedAlbum.name
        catalog.albums.first().tracks.shouldContainOnly(audioItem)
    }

    "ArtistCatalog within-album track ordering is maintained after construction" {
        val artist = Arb.artist().next()
        val albumAudioItems = files.virtualAlbumAudioFiles(artist = artist).next().map(::createAudioItem)

        val catalog = ImmutableArtistCatalog(artist, albumAudioItems)

        catalog.albums.first().tracks.shouldBeOrdered()
    }

    "ArtistCatalog deduplicates by id when both items have assigned ids" {
        val expectedArtist = Arb.artist().next()
        val expectedAlbum = AlbumDetails("Shared Album", expectedArtist)
        val path =
            files.virtualAudioFile {
                artist = expectedArtist
                album = expectedAlbum
                trackNumber = 1
                discNumber = 1
            }.next()
        val item1 = createAudioItem(path, 1)
        val item2 = createAudioItem(path, 2)

        val catalog = ImmutableArtistCatalog(expectedArtist, listOf(item1, item2))

        // Both items have different assigned ids, so they are not considered duplicates
        catalog.size shouldBe 2
        catalog.albumAudioItems(expectedAlbum.name) shouldBe setOf(item1, item2)
    }

    "ArtistCatalog deduplicates by uniqueId when both items have UNASSIGNED_ID" {
        val audioItem = Arb.audioItem().next()

        // Same reference: uniqueId match → deduplicated
        val catalog = ImmutableArtistCatalog(audioItem.artist, listOf(audioItem, audioItem))

        catalog.size shouldBe 1
    }

    "ArtistCatalog albumAudioItems returns empty set for unknown album" {
        val catalog = ImmutableArtistCatalog(Arb.artist().next(), listOf(Arb.audioItem().next()))

        catalog.albumAudioItems("Nonexistent") shouldBe emptySet()
    }

    "ArtistCatalog albums returns one entry per distinct album" {
        val artist = Arb.artist().next()
        // Same name, differing year: only full-value album identity keeps these as two distinct buckets.
        val album1 = AlbumDetails("Shared Name", artist, year = 1997)
        val album2 = AlbumDetails("Shared Name", artist, year = 1998)
        val item1 =
            Arb.audioItem {
                this.artist = artist
                this.album = album1
                trackNumber = 1
            }.next()
        val item2 =
            Arb.audioItem {
                this.artist = artist
                this.album = album2
                trackNumber = 1
            }.next()

        val catalog = ImmutableArtistCatalog(artist, listOf(item1, item2))

        catalog.albums.size shouldBe 2
    }

    "ArtistCatalog size and isEmpty reflect the item count" {
        val artist = Arb.artist().next()
        val albumItems = files.virtualAlbumAudioFiles().next().map(::createAudioItem)

        val catalog = ImmutableArtistCatalog(artist, albumItems)

        catalog.size shouldBe albumItems.size
        catalog.isEmpty shouldBe false
    }

    "ArtistCatalog equals returns true for same artist and items" {
        val audioItem = createAudioItem(files.virtualAudioFile().next())

        val catalog1 = ImmutableArtistCatalog(audioItem.artist, listOf(audioItem))
        val catalog2 = ImmutableArtistCatalog(audioItem.artist, listOf(audioItem))

        (catalog1 == catalog2) shouldBe true
        catalog1.hashCode() shouldBe catalog2.hashCode()
    }

    "ArtistCatalog equals returns false for different artist" {
        val firstArtist = Artist.of("First Artist")
        val secondArtist = Artist.of("Second Artist")
        val item1 = Arb.audioItem { artist = firstArtist }.next()
        val item2 = Arb.audioItem { artist = secondArtist }.next()

        val first = ImmutableArtistCatalog(firstArtist, listOf(item1))
        val second = ImmutableArtistCatalog(secondArtist, listOf(item2))

        (first == second) shouldBe false
    }

    "ArtistCatalog equals returns false for non-catalog types and null" {
        val catalog = ImmutableArtistCatalog(Arb.artist().next(), listOf(Arb.audioItem().next()))

        catalog.equals(null) shouldBe false
        catalog.equals("not a catalog") shouldBe false
    }

    "ArtistCatalog hashCode differs when items differ" {
        val firstArtist = Artist.of("A")
        val secondArtist = Artist.of("B")
        val item1 = Arb.audioItem { artist = firstArtist }.next()
        val item2 = Arb.audioItem { artist = secondArtist }.next()

        val catalog1 = ImmutableArtistCatalog(firstArtist, listOf(item1))
        val catalog2 = ImmutableArtistCatalog(secondArtist, listOf(item2))

        // Different artists → different hash codes (artist contributes to hash)
        catalog1.hashCode() shouldNotBe catalog2.hashCode()
    }

    "ArtistCatalog compareTo orders by artist" {
        val firstArtist = Artist.of("A Artist")
        val secondArtist = Artist.of("Z Artist")
        val item1 = Arb.audioItem { artist = firstArtist }.next()
        val item2 = Arb.audioItem { artist = secondArtist }.next()

        val first = ImmutableArtistCatalog(firstArtist, listOf(item1))
        val second = ImmutableArtistCatalog(secondArtist, listOf(item2))

        (first.compareTo(second) < 0) shouldBe true
    }

    "ArtistCatalog toString includes artist and size" {
        val audioItem = createAudioItem(files.virtualAudioFile().next())
        val catalog = ImmutableArtistCatalog(audioItem.artist, listOf(audioItem))

        catalog.toString() shouldBe "ImmutableArtistCatalog(artist=${audioItem.artist}, size=1)"
    }

    "ArtistCatalog albumAudioItems returns items for a known album" {
        val artist = Arb.artist().next()
        val albumAudioItems = files.virtualAlbumAudioFiles(artist = artist).next().map(::createAudioItem)

        val catalog = ImmutableArtistCatalog(artist, albumAudioItems)
        val albumName = albumAudioItems.first().album.name

        catalog.albumAudioItems(albumName).shouldContainOnly(albumAudioItems)
    }
})