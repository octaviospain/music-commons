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
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Direct round-trip coverage for the standalone value-type contextual serializers. The audio-item
 * entity round-trip exercises [AlbumDetails] (which nests label/artist inline), so these tests close the
 * gap for the [Label], [Artist], and [Genre] serializers when used on their own.
 */
@DisplayName("Audio-item contextual serializers")
internal class AudioItemContextualSerializersTest : StringSpec({

    val json = Json { serializersModule = audioItemSerializersModule }

    fun <T> T.decodeEncoded(serializer: KSerializer<T>): T =
        json.decodeFromString(serializer, json.encodeToString(serializer, this))

    data class RoundTripCase(val name: String, val assert: () -> Unit)

    withData(
        nameFn = { it.name },
        RoundTripCase("LabelContextualSerializer round-trips name and country code") {
            val decoded = Label.of("Warp", CountryCode.GB).decodeEncoded(LabelContextualSerializer)
            decoded.name shouldBe "Warp"
            decoded.countryCode shouldBe CountryCode.GB
        },
        RoundTripCase("LabelContextualSerializer round-trips an undefined country code") {
            val decoded = Label.of("Self-Released", CountryCode.UNDEFINED).decodeEncoded(LabelContextualSerializer)
            decoded.name shouldBe "Self-Released"
            decoded.countryCode shouldBe CountryCode.UNDEFINED
        },
        RoundTripCase("ArtistContextualSerializer round-trips name and country code") {
            val decoded = Artist.of("Aphex Twin", CountryCode.GB).decodeEncoded(ArtistContextualSerializer)
            decoded.name shouldBe "Aphex Twin"
            decoded.countryCode shouldBe CountryCode.GB
        },
        RoundTripCase("GenreContextualSerializer round-trips a known genre") {
            val genre = parseGenre("Electronic").first()
            genre.decodeEncoded(GenreContextualSerializer) shouldBe genre
        },
        RoundTripCase("GenreContextualSerializer round-trips a custom genre") {
            val genre = Genre.Custom("Hauntology")
            genre.decodeEncoded(GenreContextualSerializer) shouldBe genre
        }
    ) { it.assert() }
})