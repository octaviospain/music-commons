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

package net.transgressoft.commons.persistence.music.audio

import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.Label
import net.transgressoft.commons.music.audio.parseGenre
import com.neovisionaries.i18n.CountryCode
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * Direct round-trip coverage for the standalone value-type contextual serializers. The audio-item
 * entity round-trip exercises [AlbumDetails] (which nests label/artist inline), so these tests close the
 * gap for the [Label], [Artist], and [Genre] serializers when used on their own.
 */
@DisplayName("Audio-item contextual serializers")
internal class AudioItemContextualSerializersTest : StringSpec({

    val json = Json { serializersModule = audioItemSerializersModule }

    "LabelContextualSerializer round-trips name and country code" {
        val label = Label.of("Warp", CountryCode.GB)
        val decoded = json.decodeFromString(LabelContextualSerializer, json.encodeToString(LabelContextualSerializer, label))
        decoded.name shouldBe "Warp"
        decoded.countryCode shouldBe CountryCode.GB
    }

    "LabelContextualSerializer round-trips an undefined country code" {
        val label = Label.of("Self-Released", CountryCode.UNDEFINED)
        val decoded = json.decodeFromString(LabelContextualSerializer, json.encodeToString(LabelContextualSerializer, label))
        decoded.name shouldBe "Self-Released"
        decoded.countryCode shouldBe CountryCode.UNDEFINED
    }

    "ArtistContextualSerializer round-trips name and country code" {
        val artist = Artist.of("Aphex Twin", CountryCode.GB)
        val decoded = json.decodeFromString(ArtistContextualSerializer, json.encodeToString(ArtistContextualSerializer, artist))
        decoded.name shouldBe "Aphex Twin"
        decoded.countryCode shouldBe CountryCode.GB
    }

    "GenreContextualSerializer round-trips a known genre" {
        val genre = parseGenre("Electronic").first()
        val decoded = json.decodeFromString(GenreContextualSerializer, json.encodeToString(GenreContextualSerializer, genre))
        decoded shouldBe genre
    }

    "GenreContextualSerializer round-trips a custom genre" {
        val genre = Genre.Custom("Hauntology")
        val decoded = json.decodeFromString(GenreContextualSerializer, json.encodeToString(GenreContextualSerializer, genre))
        decoded shouldBe genre
    }
})