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
internal class ImmutableAlbumTest : StringSpec({

    val files = virtualFiles()

    fun createAudioItem(path: Path): AudioItem =
        MutableAudioItemTestBridge.createAudioItem(path, files.metadataIO)

    fun createAudioItem(path: Path, id: Int): AudioItem =
        MutableAudioItemTestBridge.createAudioItem(path, id, files.metadataIO)

    "ImmutableAlbum exposes album, size, isEmpty, and audioItems from construction list" {
        val artist = Arb.artist().next()
        val albumDetails = Arb.album(albumArtist = artist).next()
        val audioItem =
            Arb.audioItem {
                this.album = albumDetails
                trackNumber = 1
            }.next()

        val album = ImmutableAlbum(albumDetails, listOf(audioItem))

        album.album shouldBe albumDetails
        album.size shouldBe 1
        album.isEmpty shouldBe false
        album.tracks.shouldContainOnly(audioItem)
    }

    "ImmutableAlbum isEmpty is true for empty list" {
        val albumDetails = Arb.album().next()

        val album = ImmutableAlbum(albumDetails, emptyList())

        album.isEmpty shouldBe true
        album.size shouldBe 0
        album.tracks shouldBe emptyList()
    }

    "ImmutableAlbum preserves all items as passed — deduplication is the projection layer's responsibility" {
        val artist = Arb.artist().next()
        val albumDetails = AlbumDetails("Album", artist)
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = albumDetails
                trackNumber = 1
                discNumber = 1
            }.next()
        val item1 = createAudioItem(path, 1)
        val item2 = createAudioItem(path, 2)

        val album = ImmutableAlbum(albumDetails, listOf(item1, item2))

        album.size shouldBe 2
        album.tracks.shouldContainOnly(item1, item2)
    }

    "ImmutableAlbum stores tracks verbatim without re-sorting" {
        val artist = Arb.artist().next()
        val albumDetails = AlbumDetails("Verbatim", artist)
        val paths = files.virtualAlbumAudioFiles(artist, albumDetails, size = 3..5).next()
        // Reverse so the input is explicitly out of natural order; the projection delivers items
        // pre-sorted, so ImmutableAlbum must preserve the given order rather than sort it itself.
        val items = paths.mapIndexed { idx, path -> createAudioItem(path, idx + 1) }.reversed()

        val album = ImmutableAlbum(albumDetails, items)

        album.tracks shouldBe items
        album.tracks shouldNotBe items.sorted()
    }

    "ImmutableAlbum compareTo orders by album" {
        val artist = Arb.artist().next()
        val albumDetailsA = AlbumDetails("A Album", artist)
        val albumDetailsZ = AlbumDetails("Z Album", artist)
        val itemA = Arb.audioItem { album = albumDetailsA }.next()
        val itemZ = Arb.audioItem { album = albumDetailsZ }.next()

        val albumA = ImmutableAlbum(albumDetailsA, listOf(itemA))
        val albumZ = ImmutableAlbum(albumDetailsZ, listOf(itemZ))

        albumA shouldBeLessThan albumZ
        albumZ shouldBeGreaterThan albumA
        albumA shouldBeEqualComparingTo albumA
    }

    "ImmutableAlbum equals is true for same album and same items" {
        val albumDetails = Arb.album().next()
        val audioItem = Arb.audioItem { this.album = albumDetails }.next()

        val album1 = ImmutableAlbum(albumDetails, listOf(audioItem))
        val album2 = ImmutableAlbum(albumDetails, listOf(audioItem))

        (album1 == album2) shouldBe true
        album1.hashCode() shouldBe album2.hashCode()
    }

    "ImmutableAlbum with different albums is unequal in both equals and hashCode" {
        val artist = Arb.artist().next()
        val albumDetails1 = AlbumDetails("First Album", artist)
        val albumDetails2 = AlbumDetails("Second Album", artist)
        val item1 = Arb.audioItem { album = albumDetails1 }.next()
        val item2 = Arb.audioItem { album = albumDetails2 }.next()

        val album1 = ImmutableAlbum(albumDetails1, listOf(item1))
        val album2 = ImmutableAlbum(albumDetails2, listOf(item2))

        assertSoftly {
            (album1 == album2) shouldBe false
            album1.hashCode() shouldNotBe album2.hashCode()
        }
    }

    "ImmutableAlbum equals returns false for null and non-album types" {
        val albumDetails = Arb.album().next()
        val album = ImmutableAlbum(albumDetails, listOf(Arb.audioItem { this.album = albumDetails }.next()))

        album.equals(null) shouldBe false
        album.equals("not an album") shouldBe false
    }

    "ImmutableAlbum toString includes album and size" {
        val artist = Artist.of("Test Artist")
        val albumDetails = AlbumDetails("Test Album", artist)
        val audioItem = Arb.audioItem { this.album = albumDetails }.next()

        val album = ImmutableAlbum(albumDetails, listOf(audioItem))

        album.toString() shouldBe "ImmutableAlbum(album=$albumDetails, size=1)"
    }

    "ImmutableAlbum clone returns itself" {
        val albumDetails = Arb.album().next()
        val album = ImmutableAlbum(albumDetails, listOf(Arb.audioItem { this.album = albumDetails }.next()))

        (album.clone() === album) shouldBe true
    }

    "ImmutableAlbum coverImageBytes returns cover of first covered item" {
        val albumDetails = Arb.album().next()
        val expectedCover = byteArrayOf(10, 20, 30)
        val itemNoCover =
            Arb.audioItem {
                this.album = albumDetails
                trackNumber = 1
                discNumber = 1
                coverImageBytes = null
            }.next()
        val itemWithCover =
            Arb.audioItem {
                this.album = albumDetails
                trackNumber = 2
                discNumber = 1
                coverImageBytes = expectedCover
            }.next()

        val album = ImmutableAlbum(albumDetails, listOf(itemNoCover, itemWithCover))

        album.coverImageBytes shouldBe expectedCover
    }

    "ImmutableAlbum coverImageBytes returns null when no item has a cover" {
        val albumDetails = Arb.album().next()
        val item1 =
            Arb.audioItem {
                this.album = albumDetails
                trackNumber = 1
                coverImageBytes = null
            }.next()
        val item2 =
            Arb.audioItem {
                this.album = albumDetails
                trackNumber = 2
                coverImageBytes = null
            }.next()

        val album = ImmutableAlbum(albumDetails, listOf(item1, item2))

        album.coverImageBytes shouldBe null
    }

    "ImmutableAlbum retains two distinct UNASSIGNED_ID items with same disc and track" {
        val artist = Arb.artist().next()
        val albumDetails = AlbumDetails("Identity Album", artist)
        val path1 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = albumDetails
                trackNumber = 1
                discNumber = 1
            }.next()
        val path2 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = albumDetails
                trackNumber = 1
                discNumber = 1
            }.next()
        val item1 = createAudioItem(path1)
        val item2 = createAudioItem(path2)

        val album = ImmutableAlbum(albumDetails, listOf(item1, item2))

        album.size shouldBe 2
        album.tracks.shouldContainOnly(item1, item2)
    }
})