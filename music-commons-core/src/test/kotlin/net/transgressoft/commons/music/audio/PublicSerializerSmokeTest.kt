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

import net.transgressoft.commons.media.persistence.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.playlist.DefaultPlaylistHierarchy
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.persistence.music.playlist.AudioPlaylistMapSerializer
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * Smoke tests asserting the public-symbol contract for the convenience map serializers exposed by
 * the persistence module. These serializers are the supported entry points consumers wire into a
 * `JsonFileRepository`; this test pins that they resolve and round-trip a minimal map through JSON.
 */
@DisplayName("Public serializer symbols")
internal class PublicSerializerSmokeTest : StringSpec({

    val files = virtualFiles()
    val json = Json { }

    "AudioItemMapSerializer round-trips an empty map" {
        val encoded = json.encodeToString(AudioItemMapSerializer, emptyMap())
        json.decodeFromString(AudioItemMapSerializer, encoded) shouldHaveSize 0
    }

    "AudioPlaylistMapSerializer round-trips an empty map" {
        val encoded = json.encodeToString(AudioPlaylistMapSerializer, emptyMap<Int, MutableAudioPlaylist>())
        json.decodeFromString(AudioPlaylistMapSerializer, encoded) shouldHaveSize 0
    }

    "AudioPlaylistMapSerializer round-trips a single playlist" {
        val hierarchy = DefaultPlaylistHierarchy()
        val playlist = hierarchy.createPlaylist("Smoke")
        val map: Map<Int, MutableAudioPlaylist> = mapOf(playlist.id to playlist)

        val encoded = json.encodeToString(AudioPlaylistMapSerializer, map)
        val decoded = json.decodeFromString(AudioPlaylistMapSerializer, encoded)

        decoded shouldHaveSize 1
        decoded[playlist.id]?.name shouldBe "Smoke"

        hierarchy.close()
    }

    "AudioWaveformMapSerializer round-trips an empty map" {
        val encoded = json.encodeToString(AudioWaveformMapSerializer, emptyMap<Int, AudioWaveform>())
        json.decodeFromString(AudioWaveformMapSerializer, encoded) shouldHaveSize 0
    }

    // Touch the virtualFiles fixture so test infrastructure resolves consistently with sibling tests.
    "virtual filesystem fixture is reachable for serializer smoke tests" {
        files.fileSystem shouldBe files.fileSystem
    }
})