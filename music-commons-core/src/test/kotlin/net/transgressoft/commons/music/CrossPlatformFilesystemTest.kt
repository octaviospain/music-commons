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

package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.ImmutableLabel
import net.transgressoft.commons.music.audio.MutableAudioItemTestBridge
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.music.playlist.AudioPlaylistMapSerializer
import net.transgressoft.commons.music.playlist.MutablePlaylist
import net.transgressoft.commons.music.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.neovisionaries.i18n.CountryCode
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cross-platform regression safety net for JSON round-trip of path-bearing entities.
 *
 * Exercises [AudioItem], [ScalableAudioWaveform], and [MutablePlaylist] under Jimfs
 * unix, windows, and osx configurations so that Windows-only path parsing bugs
 * (e.g. native [java.nio.file.Paths.get] rejecting characters or separators that the
 * unix-based JimFs fixture accepted) are caught during local Linux builds instead of
 * surfacing only on the Windows CI matrix.
 *
 * Each row label (`unix` / `windows` / `osx`) is embedded in the test name so failure
 * output immediately identifies the failing platform.
 */
@DisplayName("Cross-platform Jimfs filesystem")
internal class CrossPlatformFilesystemTest : StringSpec({

    val configs: Map<String, Configuration> =
        mapOf(
            "unix" to Configuration.unix(),
            "windows" to Configuration.windows(),
            "osx" to Configuration.osX()
        )

    val json =
        Json {
            prettyPrint = false
            explicitNulls = true
        }

    // ASCII-only fixtures keep osx NFD normalization transparent — osx Jimfs decomposes
    // Unicode combining characters on path creation, which would make decoded.path.toString()
    // differ from original.path.toString() if the test data contained precomposed characters.
    val asciiArtist = ImmutableArtist.of("Round Trip Artist", CountryCode.US)
    val asciiLabel = ImmutableLabel.of("Round Trip Label")
    val asciiAlbum = ImmutableAlbum("Round Trip Album", asciiArtist, false, 2020.toShort(), asciiLabel)

    // Stable per-label id offsets — String.hashCode() is JVM-specified for String, but
    // an explicit map is more obvious and immune to silent collisions if labels change.
    val idOffsetByLabel = mapOf("unix" to 1, "windows" to 2, "osx" to 3)

    configs.forEach { (label, config) ->
        val offset = idOffsetByLabel.getValue(label)

        "AudioItem JSON round-trip preserves path and key fields on $label Jimfs" {
            Jimfs.newFileSystem(config).use { fs ->
                val path =
                    Arb.virtualAudioFile(fileSystem = fs) {
                        this.title = "Round Trip $label"
                        this.artist = asciiArtist
                        this.album = asciiAlbum
                        this.trackNumber = 1
                        this.discNumber = 1
                    }.next()
                val originalId = 1000 + offset
                val original: AudioItem = MutableAudioItemTestBridge.createAudioItem(path, originalId)

                val encoded = json.encodeToString(AudioItemMapSerializer, mapOf(original.id to original))
                val decoded = json.decodeFromString(AudioItemMapSerializer, encoded).getValue(original.id)

                decoded.path.toString() shouldBe original.path.toString()
                decoded.id shouldBe original.id
                decoded.title shouldBe original.title
                decoded.artist.name shouldBe original.artist.name
                decoded.album.name shouldBe original.album.name
            }
        }

        "AudioWaveform JSON round-trip preserves audioFilePath on $label Jimfs" {
            Jimfs.newFileSystem(config).use { fs ->
                val path =
                    Arb.virtualAudioFile(fileSystem = fs) {
                        this.title = "Waveform $label"
                        this.artist = asciiArtist
                        this.album = asciiAlbum
                    }.next()
                val amplitudes = floatArrayOf(0.0f, 0.5f, 1.0f, 0.25f)
                val originalId = 2000 + offset
                val original = ScalableAudioWaveform(originalId, path, amplitudes.size, amplitudes)

                val encoded = json.encodeToString(AudioWaveformMapSerializer, mapOf(original.id to original))
                val decoded = json.decodeFromString(AudioWaveformMapSerializer, encoded).getValue(original.id) as ScalableAudioWaveform

                decoded.audioFilePath.toString() shouldBe original.audioFilePath.toString()
                decoded.id shouldBe original.id
                decoded.cachedWidth shouldBe original.cachedWidth
                decoded.normalizedAmplitudesSnapshot!!.toList() shouldBe amplitudes.toList()
            }
        }

        "AudioPlaylist JSON round-trip preserves tracked audio item ids on $label Jimfs" {
            Jimfs.newFileSystem(config).use { fs ->
                val path =
                    Arb.virtualAudioFile(fileSystem = fs) {
                        this.title = "Playlist Item $label"
                        this.artist = asciiArtist
                        this.album = asciiAlbum
                    }.next()
                val audioItemId = 3000 + offset
                MutableAudioItemTestBridge.createAudioItem(path, audioItemId)

                val playlistId = 4000 + offset
                val playlist =
                    MutablePlaylist(
                        id = playlistId,
                        name = "$label playlist",
                        isDirectory = false,
                        initialAudioItemIds = listOf(audioItemId)
                    )

                val encoded = json.encodeToString(AudioPlaylistMapSerializer, mapOf(playlist.id to playlist))
                // The lirp-based playlist serializer emits audioItem references as an `audioItems` JSON
                // array of ids (see AudioPlaylistSerializerTest). Assert directly on the encoded payload
                // rather than on the decoded aggregate proxy, which would require the item's repository
                // to be registered in a LirpContext for lazy resolution.
                val encodedPlaylist = Json.parseToJsonElement(encoded).jsonObject["$playlistId"]!!.jsonObject
                val encodedIds = encodedPlaylist["audioItems"]!!.jsonArray.map { it.jsonPrimitive.int }

                encodedIds shouldBe listOf(audioItemId)

                val decoded = json.decodeFromString(AudioPlaylistMapSerializer, encoded).getValue(playlist.id)

                decoded.name shouldBe playlist.name
                decoded.isDirectory shouldBe playlist.isDirectory
                decoded.audioItems.size shouldBe 1
            }
        }
    }
})