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

package net.transgressoft.commons.persistence.music.playlist

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next

@DisplayName("AudioPlaylist JSON round-trip")
internal class AudioPlaylistSerializerRoundTripTest : StringSpec({

    val reactive = reactiveScope()

    "AudioPlaylistMapSerializer round-trips a playlist hierarchy with restored audio-item id references" {
        val playlistsFile = tempfile("playlistHierarchy-rt", ".json").apply { deleteOnExit() }
        val audioFile = tempfile("audioLibrary-rt-pl", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .build()

        val item1 = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val item2 = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val playlist = library.createPlaylist("Round Trip Playlist")
        playlist.addAudioItems(listOf(item1, item2))
        reactive.advance()

        val playlistId = playlist.id
        val expectedIds = listOf(item1.id, item2.id)

        library.close()

        val reloaded =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .build()

        val restored = reloaded.findPlaylistByName("Round Trip Playlist").orElse(null)
        restored.shouldNotBeNull()
        restored.id shouldBe playlistId
        restored.audioItems.map { it.id } shouldContainExactlyInAnyOrder expectedIds

        reloaded.close()
    }
})