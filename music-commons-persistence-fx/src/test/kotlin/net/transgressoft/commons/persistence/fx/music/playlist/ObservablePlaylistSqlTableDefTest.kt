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

package net.transgressoft.commons.persistence.fx.music.playlist

import net.transgressoft.commons.fx.music.FXMusicLibrary
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylist
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.persistence.fx.music.audio.ObservableAudioItemMapSerializer
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.sql.SqliteRepository
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@DisplayName("ObservablePlaylistSqlTableDef")
internal class ObservablePlaylistSqlTableDefTest : StringSpec({

    val reactive = reactiveScope()

    beforeSpec { FxToolkit.registerPrimaryStage() }

    "ObservablePlaylistSqlTableDef round-trips FX playlists through SQLite preserving identity, folder flag and aggregate id references" {
        val dbFile = tempfile("fxPlaylistHierarchy-sql", ".db").apply { deleteOnExit() }
        val audioFile = tempfile("fxAudioLibrary-sql-pl", ".json").apply { deleteOnExit() }

        val library =
            FXMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
                .playlistRepository(SqliteRepository.fileBacked(dbFile.toPath(), ObservablePlaylistSqlTableDef, false))
                .build()

        val item1 = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val item2 = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val item3 = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())

        val rock = library.createPlaylist("Rock", listOf(item1, item2))
        val jazz = library.createPlaylist("Jazz", listOf(item3))
        val folders = library.createPlaylistDirectory("Folders")
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        val rockId = rock.id
        val jazzId = jazz.id
        val folderId = folders.id

        library.close()

        val reloaded =
            FXMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
                .playlistRepository(SqliteRepository.fileBacked(dbFile.toPath(), ObservablePlaylistSqlTableDef, false))
                .build()
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        reloaded.playlistHierarchy().size() shouldBe 3

        val restoredRock = reloaded.playlistHierarchy().findByName("Rock").orElse(null) as? ObservablePlaylist
        restoredRock.shouldNotBeNull()
        restoredRock.id shouldBe rockId
        restoredRock.isDirectory shouldBe false
        restoredRock.audioItems.map { it.id } shouldContainExactlyInAnyOrder listOf(item1.id, item2.id)

        val restoredJazz = reloaded.playlistHierarchy().findByName("Jazz").orElse(null) as? ObservablePlaylist
        restoredJazz.shouldNotBeNull()
        restoredJazz.id shouldBe jazzId
        restoredJazz.audioItems.map { it.id } shouldContainExactlyInAnyOrder listOf(item3.id)

        val restoredFolder = reloaded.playlistHierarchy().findByName("Folders").orElse(null) as? ObservablePlaylist
        restoredFolder.shouldNotBeNull()
        restoredFolder.id shouldBe folderId
        restoredFolder.isDirectory shouldBe true
        restoredFolder.audioItems.shouldBeEmpty()

        reloaded.close()
    }

    "ObservablePlaylistSqlTableDef constructs playlists without firing CrudEvents and keeps reactive wiring live after load" {
        val dbFile = tempfile("fxPlaylistHierarchy-events", ".db").apply { deleteOnExit() }
        val audioFile = tempfile("fxAudioLibrary-events-pl", ".json").apply { deleteOnExit() }

        val seedLibrary =
            FXMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
                .playlistRepository(SqliteRepository.fileBacked(dbFile.toPath(), ObservablePlaylistSqlTableDef, false))
                .build()
        val seedItem = seedLibrary.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val seededId = seedLibrary.createPlaylist("Seeded", listOf(seedItem)).id
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()
        seedLibrary.close()

        val library =
            FXMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
                .playlistRepository(SqliteRepository.fileBacked(dbFile.toPath(), ObservablePlaylistSqlTableDef, false))
                .build()
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        library.playlistHierarchy().size() shouldBe 1

        // Reactive wiring is live after construction-free bulk load: a post-load mutation publishes
        // exactly one MutationEvent through the reconstructed playlist's own change stream.
        val restored = library.playlistHierarchy().findByName("Seeded").orElse(null) as? ObservablePlaylist
        restored.shouldNotBeNull()
        restored.id shouldBe seededId
        val mutationEvents = AtomicInteger(0)
        restored.subscribe { mutationEvents.incrementAndGet() }
        restored.name = "Renamed"
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()
        mutationEvents.get() shouldBe 1

        library.close()
    }

    "ObservablePlaylistSqlTableDef declares the constructor-param columns the in-base raw constructor expects" {
        val columnNames = ObservablePlaylistSqlTableDef.columns.map { it.name }.toSet()
        columnNames.containsAll(listOf("id", "name", "is_directory", "audio_item_ids", "playlist_ids")) shouldBe true
        ObservablePlaylistSqlTableDef.entityClassName shouldBe "net.transgressoft.commons.fx.music.playlist.FXPlaylist"
    }
})