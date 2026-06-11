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

@DisplayName("GenreExtensions")
internal class GenreExtensionsTest : StringSpec({

    "parseGenre returns empty set for blank string" {
        parseGenre("") shouldBe emptySet()
        parseGenre("   ") shouldBe emptySet()
    }

    "parseGenre resolves known genre by exact name" {
        parseGenre("Rock") shouldBe setOf(Rock)
    }

    "parseGenre resolves known genre case-insensitively" {
        parseGenre("rock") shouldBe setOf(Rock)
        parseGenre("ROCK") shouldBe setOf(Rock)
    }

    "parseGenre wraps unknown segment in Custom" {
        val result = parseGenre("NotAGenre123")
        result shouldBe setOf(Genre.Custom("NotAGenre123"))
    }

    "parseGenre splits comma-separated genres into set" {
        val result = parseGenre("Rock, Alternative")
        result shouldBe setOf(Rock, Alternative)
    }

    "joinGenres produces sorted comma-separated string" {
        val result = joinGenres(setOf(Rock, Alternative, Blues))
        result shouldBe "Alternative, Blues, Rock"
    }

    "joinGenres returns empty string for empty set" {
        joinGenres(emptySet()) shouldBe ""
    }

    "parseGenre and joinGenres round-trip a set of known genres" {
        val genres = setOf(Rock, Alternative, Jazz)
        parseGenre(joinGenres(genres)) shouldBe genres
    }
})