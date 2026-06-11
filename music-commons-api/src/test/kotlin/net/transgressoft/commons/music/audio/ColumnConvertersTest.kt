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

import com.neovisionaries.i18n.CountryCode
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Path
import java.time.Duration

@DisplayName("ColumnConverters")
internal class ColumnConvertersTest : StringSpec({

    "PathConverter round-trips a local file path via URI" {
        val path = Path.of("/tmp/test/audio.mp3").toAbsolutePath()
        PathConverter.fromSql(PathConverter.toSql(path)) shouldBe path
    }

    "PathConverter toSql encodes path as URI string" {
        val path = Path.of("/tmp/test/audio.mp3").toAbsolutePath()
        PathConverter.toSql(path) shouldBe path.toUri().toString()
        PathConverter.toSql(path) shouldStartWith "file:"
    }

    "DurationConverter round-trips whole-second durations" {
        val duration = Duration.ofSeconds(245)
        DurationConverter.fromSql(DurationConverter.toSql(duration)) shouldBe duration
    }

    "DurationConverter toSql returns total seconds as Long" {
        val duration = Duration.ofMinutes(4).plusSeconds(5)
        DurationConverter.toSql(duration) shouldBe 245L
    }

    "DurationConverter round-trips Duration.ZERO" {
        DurationConverter.fromSql(DurationConverter.toSql(Duration.ZERO)) shouldBe Duration.ZERO
    }

    "GenreConverter round-trips a standard Genre" {
        val genre = Rock
        GenreConverter.fromSql(GenreConverter.toSql(genre)) shouldBe genre
    }

    "GenreConverter round-trips a custom genre" {
        val genre = Genre.Custom("MyCustomGenreXYZ")
        GenreConverter.fromSql(GenreConverter.toSql(genre)) shouldBe genre
    }

    "GenreConverter toSql returns genre name string" {
        GenreConverter.toSql(Rock) shouldBe "Rock"
    }

    "CountryConverter round-trips a named CountryCode" {
        val code = CountryCode.ES
        CountryConverter.fromSql(CountryConverter.toSql(code)) shouldBe code
    }

    "CountryConverter round-trips CountryCode.UNDEFINED" {
        CountryConverter.fromSql(CountryConverter.toSql(CountryCode.UNDEFINED)) shouldBe CountryCode.UNDEFINED
    }

    "CountryConverter toSql returns alpha2 string" {
        CountryConverter.toSql(CountryCode.ES) shouldBe "ES"
    }
})