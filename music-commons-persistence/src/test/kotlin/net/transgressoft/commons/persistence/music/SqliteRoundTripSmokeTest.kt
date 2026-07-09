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

package net.transgressoft.commons.persistence.music

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.testing.registryIsolation
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.persistence.music.audio.MutableAudioItemSqlTableDef
import net.transgressoft.commons.persistence.music.playlist.AudioPlaylistMapSerializer
import net.transgressoft.commons.persistence.music.playlist.AudioPlaylistSqlTableDef
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.sql.SqliteRepository
import io.kotest.assertions.assertSoftly
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.io.File

/**
 * Focused smoke test confirming that the Core audio-item/playlist round-trip through a
 * [SqliteRepository] preserves the id and [AudioItem.uniqueId] identity contract.
 *
 * This is a targeted smoke test, not a full field-fidelity matrix — detailed field coverage lives
 * in [net.transgressoft.commons.persistence.music.audio.MutableAudioItemSqlTableDefTest] and
 * [net.transgressoft.commons.persistence.music.playlist.AudioPlaylistSqlTableDefTest].
 */
@DisplayName("SQLite round-trip smoke test — id and uniqueId identity")
internal class SqliteRoundTripSmokeTest : StringSpec({

    registryIsolation()
    val reactive = reactiveScope()

    "SqliteRepository audio-item round-trip preserves id and uniqueId identity" {
        val dbFile = File.createTempFile("smoke-audio-sql", ".db").apply { deleteOnExit() }
        val playlistsFile = File.createTempFile("smoke-playlists-sql", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), MutableAudioItemSqlTableDef))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .build()

        val original = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val originalId = original.id
        val originalUniqueId = original.uniqueId
        reactive.advance()

        library.close()

        val reloaded =
            CoreMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), MutableAudioItemSqlTableDef))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .build()

        val loaded = reloaded.audioLibrary().findById(originalId).orElse(null)
        loaded.shouldNotBeNull()
        assertSoftly {
            loaded.id shouldBe originalId
            loaded.uniqueId shouldBe originalUniqueId
        }

        reloaded.close()
    }

    "SqliteRepository playlist round-trip preserves id and uniqueId identity" {
        val audioFile = File.createTempFile("smoke-audio-json", ".json").apply { deleteOnExit() }
        val dbFile = File.createTempFile("smoke-playlists-db", ".db").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(SqliteRepository.fileBacked(dbFile.toPath(), AudioPlaylistSqlTableDef))
                .build()

        val audioItem = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val playlist = library.createPlaylist("SmokeTest", listOf(audioItem))
        val originalPlaylistId = playlist.id
        val originalPlaylistUniqueId = playlist.uniqueId
        reactive.advance()

        library.close()

        val reloaded =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(SqliteRepository.fileBacked(dbFile.toPath(), AudioPlaylistSqlTableDef))
                .build()
        reactive.advance()

        val restoredPlaylist = reloaded.findPlaylistByName("SmokeTest").orElse(null)
        restoredPlaylist.shouldNotBeNull()
        assertSoftly {
            restoredPlaylist.id shouldBe originalPlaylistId
            restoredPlaylist.uniqueId shouldBe originalPlaylistUniqueId
        }

        reloaded.close()
    }
})