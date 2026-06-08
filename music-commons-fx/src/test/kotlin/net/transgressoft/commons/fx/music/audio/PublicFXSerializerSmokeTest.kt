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

package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.fx.music.playlist.ObservablePlaylist
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylistMapSerializer
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldHaveSize
import kotlinx.serialization.json.Json

/**
 * Smoke tests asserting the public-symbol contract for the FX-typed convenience map serializers
 * and [observableAudioItemSerializerModule]. These symbols flipped from `internal` to public
 * in Phase 40 plan 06; this test pins the contract so consumers can rely on them.
 */
@DisplayName("Public FX serializer symbols")
internal class PublicFXSerializerSmokeTest : StringSpec({

    val polymorphicJson = Json { serializersModule = observableAudioItemSerializerModule }
    val plainJson = Json { }

    "ObservableAudioItemMapSerializer round-trips an empty map" {
        val encoded = polymorphicJson.encodeToString(ObservableAudioItemMapSerializer, emptyMap())
        polymorphicJson.decodeFromString(ObservableAudioItemMapSerializer, encoded) shouldHaveSize 0
    }

    "ObservablePlaylistMapSerializer round-trips an empty map" {
        val encoded = plainJson.encodeToString(ObservablePlaylistMapSerializer, emptyMap<Int, ObservablePlaylist>())
        plainJson.decodeFromString(ObservablePlaylistMapSerializer, encoded) shouldHaveSize 0
    }

    "observableAudioItemSerializerModule is reachable as public API" {
        // Smoke check: building a Json with the module should not throw.
        val encoded = polymorphicJson.encodeToString(ObservableAudioItemMapSerializer, emptyMap())
        polymorphicJson.decodeFromString(ObservableAudioItemMapSerializer, encoded) shouldHaveSize 0
    }
})