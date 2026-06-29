/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

@DisplayName("AlbumDetailsExtensions")
internal class AlbumDetailsExtensionsTest : StringSpec({

    val namedArtist = Artist.of("Radiohead")
    val anotherArtist = Artist.of("Portishead")

    "AlbumDetailsExtensions canonicalKey is idempotent" {
        val album = AlbumDetails("OK Computer", namedArtist, false, 1997, Label.of("Parlophone"))
        val key = album.canonicalKey()
        key.canonicalKey() shouldBe key
    }

    "AlbumDetailsExtensions canonicalKey lowercases and trims the album name" {
        val album1 = AlbumDetails("  Album  Name ", namedArtist)
        val album2 = AlbumDetails("album name", namedArtist)
        album1.canonicalKey() shouldBe album2.canonicalKey()
    }

    "AlbumDetailsExtensions canonicalKey collapses internal whitespace" {
        val album1 = AlbumDetails("Album  Name", namedArtist)
        val album2 = AlbumDetails("Album Name", namedArtist)
        album1.canonicalKey() shouldBe album2.canonicalKey()
    }

    "AlbumDetailsExtensions canonicalKey zeroes year, label, and isCompilation" {
        val album = AlbumDetails("OK Computer", namedArtist, true, 1997, Label.of("Parlophone"))
        val key = album.canonicalKey()
        key.year shouldBe null
        key.label shouldBe Label.UNKNOWN
        key.isCompilation shouldBe false
    }

    "AlbumDetailsExtensions canonicalKey collapses albumArtist to UNKNOWN for explicit compilation" {
        val compilationAlbum = AlbumDetails("Cherry Moon 9", namedArtist, isCompilation = true)
        compilationAlbum.canonicalKey().albumArtist shouldBe Artist.UNKNOWN
    }

    "AlbumDetailsExtensions canonicalKey collapses albumArtist to UNKNOWN for UNKNOWN artist" {
        val unknownArtistAlbum = AlbumDetails("Cherry Moon 9", Artist.UNKNOWN)
        unknownArtistAlbum.canonicalKey().albumArtist shouldBe Artist.UNKNOWN
    }

    "AlbumDetailsExtensions canonicalKey collapses albumArtist to UNKNOWN for blank artist name" {
        val blankArtistAlbum = AlbumDetails("Cherry Moon 9", Artist.of(""))
        blankArtistAlbum.canonicalKey().albumArtist shouldBe Artist.UNKNOWN
    }

    "AlbumDetailsExtensions canonicalKey collapses Various Artists to UNKNOWN (case-insensitive)" {
        val variousArtistsAlbum = AlbumDetails("Cherry Moon 9", Artist.of("Various Artists"))
        variousArtistsAlbum.canonicalKey().albumArtist shouldBe Artist.UNKNOWN

        val variousArtistsMixed = AlbumDetails("Cherry Moon 9", Artist.of("various artists"))
        variousArtistsMixed.canonicalKey().albumArtist shouldBe Artist.UNKNOWN
    }

    "AlbumDetailsExtensions canonicalKey merges compilation and Various Artists tracks with same name" {
        val compilationAlbum = AlbumDetails("Cherry Moon 9", Artist.of(""), isCompilation = true)
        val variousArtistsAlbum = AlbumDetails("Cherry Moon 9", Artist.of("Various Artists"), isCompilation = false)
        compilationAlbum.canonicalKey() shouldBe variousArtistsAlbum.canonicalKey()
    }

    "AlbumDetailsExtensions canonicalKey keeps different named artists distinct" {
        val radioheadAlbum = AlbumDetails("Pablo Honey", namedArtist)
        val portisheadAlbum = AlbumDetails("Pablo Honey", anotherArtist)
        radioheadAlbum.canonicalKey() shouldNotBe portisheadAlbum.canonicalKey()
    }

    "AlbumDetailsExtensions canonicalKey merges named-artist casing and whitespace variants" {
        val album1 = AlbumDetails("Discovery", Artist.of("Daft Punk"))
        val album2 = AlbumDetails("Discovery", Artist.of("  daft   punk "))
        album1.canonicalKey() shouldBe album2.canonicalKey()
    }

    "AlbumDetailsExtensions canonicalKey produces usable key for blank album name" {
        val blankAlbum = AlbumDetails("", namedArtist)
        val nonBlankAlbum = AlbumDetails("OK Computer", namedArtist)
        blankAlbum.canonicalKey() shouldNotBe nonBlankAlbum.canonicalKey()
    }

    "AlbumDetailsExtensions isCompilationAlbum returns true for isCompilation flag" {
        val album = AlbumDetails("Cherry Moon 9", namedArtist, isCompilation = true)
        album.isCompilationAlbum() shouldBe true
    }

    "AlbumDetailsExtensions isCompilationAlbum returns true for Artist.UNKNOWN" {
        val album = AlbumDetails("Cherry Moon 9", Artist.UNKNOWN)
        album.isCompilationAlbum() shouldBe true
    }

    "AlbumDetailsExtensions isCompilationAlbum returns true for blank albumArtist" {
        val album = AlbumDetails("Cherry Moon 9", Artist.of(""))
        album.isCompilationAlbum() shouldBe true
    }

    "AlbumDetailsExtensions isCompilationAlbum returns true for Various Artists (case-insensitive)" {
        AlbumDetails("Cherry Moon 9", Artist.of("Various Artists")).isCompilationAlbum() shouldBe true
        AlbumDetails("Cherry Moon 9", Artist.of("various artists")).isCompilationAlbum() shouldBe true
        AlbumDetails("Cherry Moon 9", Artist.of("VARIOUS ARTISTS")).isCompilationAlbum() shouldBe true
    }

    "AlbumDetailsExtensions isCompilationAlbum returns false for normal named artist" {
        val album = AlbumDetails("OK Computer", namedArtist)
        album.isCompilationAlbum() shouldBe false
    }
})