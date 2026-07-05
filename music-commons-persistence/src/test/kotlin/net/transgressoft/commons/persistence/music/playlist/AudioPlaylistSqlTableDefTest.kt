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
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.sql.SqliteRepository
import io.kotest.assertions.assertSoftly
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("AudioPlaylistSqlTableDef")
internal class AudioPlaylistSqlTableDefTest : StringSpec({

    val reactive = reactiveScope()

    "AudioPlaylistSqlTableDef round-trips playlists through SQLite preserving per-row fidelity and aggregate id references" {
        val dbFile = File.createTempFile("playlistHierarchy-sql", ".db").apply { deleteOnExit() }
        val audioFile = File.createTempFile("audioLibrary-sql-pl", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(SqliteRepository.fileBacked(dbFile.toPath(), AudioPlaylistSqlTableDef))
                .build()

        val item1 = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val item2 = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val item3 = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())

        val rock = library.createPlaylist("Rock", listOf(item1, item2))
        val jazz = library.createPlaylist("Jazz", listOf(item3))
        val emptyDir = library.createPlaylistDirectory("Folders")
        reactive.advance()

        val rockId = rock.id
        val jazzId = jazz.id
        val folderId = emptyDir.id

        library.close()

        val reloaded =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(SqliteRepository.fileBacked(dbFile.toPath(), AudioPlaylistSqlTableDef))
                .build()
        reactive.advance()

        reloaded.playlistHierarchy().size() shouldBe 3

        val restoredRock = reloaded.findPlaylistByName("Rock").orElse(null)
        restoredRock.shouldNotBeNull()
        assertSoftly {
            restoredRock.id shouldBe rockId
            restoredRock.isDirectory shouldBe false
            restoredRock.audioItems.map { it.id } shouldContainExactlyInAnyOrder listOf(item1.id, item2.id)
        }

        val restoredJazz = reloaded.findPlaylistByName("Jazz").orElse(null)
        restoredJazz.shouldNotBeNull()
        assertSoftly {
            restoredJazz.id shouldBe jazzId
            restoredJazz.audioItems.map { it.id } shouldContainExactlyInAnyOrder listOf(item3.id)
        }

        val restoredFolder = reloaded.findPlaylistByName("Folders").orElse(null)
        restoredFolder.shouldNotBeNull()
        assertSoftly {
            restoredFolder.id shouldBe folderId
            restoredFolder.isDirectory shouldBe true
            restoredFolder.audioItems.shouldBeEmpty()
        }

        reloaded.close()
    }

    "AudioPlaylistSqlTableDef keeps reactive wiring live after construction-free load" {
        val dbFile = File.createTempFile("playlistHierarchy-events", ".db").apply { deleteOnExit() }
        val audioFile = File.createTempFile("audioLibrary-events-pl", ".json").apply { deleteOnExit() }

        val seedLibrary =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(SqliteRepository.fileBacked(dbFile.toPath(), AudioPlaylistSqlTableDef))
                .build()
        val seedItem = seedLibrary.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val seededId = seedLibrary.createPlaylist("Seeded", listOf(seedItem)).id
        reactive.advance()
        seedLibrary.close()

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .playlistRepository(SqliteRepository.fileBacked(dbFile.toPath(), AudioPlaylistSqlTableDef))
                .build()
        reactive.advance()

        library.playlistHierarchy().size() shouldBe 1

        // Reactive wiring is live after construction-free bulk load: a post-load mutation publishes
        // exactly one MutationEvent through the reconstructed playlist's own change stream.
        val restored = library.findPlaylistByName("Seeded").orElse(null) as? MutableAudioPlaylist
        restored.shouldNotBeNull()
        restored.id shouldBe seededId
        val mutationEvents = AtomicInteger(0)
        restored.subscribe { mutationEvents.incrementAndGet() }
        restored.name = "Renamed"
        reactive.advance()
        mutationEvents.get() shouldBe 1

        library.close()
    }

    "AudioPlaylistSqlTableDef declares the constructor-param columns the in-base raw constructor expects" {
        val columnNames = AudioPlaylistSqlTableDef.columns.map { it.name }
        columnNames shouldContainAll listOf("id", "name", "is_directory", "audio_item_ids", "playlist_ids")
        AudioPlaylistSqlTableDef.entityClassName shouldBe "net.transgressoft.commons.music.playlist.MutablePlaylist"
    }
})