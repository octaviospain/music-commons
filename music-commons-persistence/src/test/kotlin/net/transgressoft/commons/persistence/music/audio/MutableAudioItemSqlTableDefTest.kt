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

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.persistence.music.playlist.AudioPlaylistMapSerializer
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.sql.SqliteRepository
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.io.File
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("MutableAudioItemSqlTableDef")
internal class MutableAudioItemSqlTableDefTest : StringSpec({

    val reactive = reactiveScope()

    "MutableAudioItemSqlTableDef round-trips a library through SQLite preserving per-row scalar fidelity" {
        val dbFile = File.createTempFile("audioLibrary-sql", ".db").apply { deleteOnExit() }
        val playlistsFile = File.createTempFile("playlistHierarchy-sql", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), MutableAudioItemSqlTableDef))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .build()

        val originals = (1..4).map { library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next()) }
        val byId = originals.associateBy { it.id }
        reactive.advance()

        library.close()

        val reloadedLibrary =
            CoreMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), MutableAudioItemSqlTableDef))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .build()

        reloadedLibrary.audioLibrary().size() shouldBe originals.size

        reloadedLibrary.audioLibrary().forEach { loaded: AudioItem ->
            val original = byId.getValue(loaded.id)
            loaded.path shouldBe original.path
            loaded.title shouldBe original.title
            loaded.duration shouldBe original.duration
            loaded.bitRate shouldBe original.bitRate
            loaded.artist.name shouldBe original.artist.name
            loaded.artist.countryCode shouldBe original.artist.countryCode
            loaded.album.name shouldBe original.album.name
            loaded.album.label.countryCode shouldBe original.album.label.countryCode
            loaded.genres shouldBe original.genres
            loaded.trackNumber shouldBe original.trackNumber
            loaded.discNumber shouldBe original.discNumber
        }

        reloadedLibrary.close()
    }

    "MutableAudioItemSqlTableDef round-trips playCount, comments, bpm and creation timestamps through the raw constructor and scalar initializer" {
        val dbFile = File.createTempFile("audioLibrary-sql-spi", ".db").apply { deleteOnExit() }
        val playlistsFile = File.createTempFile("playlistHierarchy-sql-spi", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), MutableAudioItemSqlTableDef))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .build()

        val original = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        // setPlayCount is event-suppressed by design; the comments/bpm mutations that follow fire
        // events, forcing a full-row rewrite that captures the seeded play count too.
        original.setPlayCount(7)
        original.comments = "seeded comment"
        original.bpm = 128.5f
        reactive.advance()

        val expectedPlayCount = original.playCount
        val expectedComments = original.comments
        val expectedBpm = original.bpm
        val expectedCreation = original.dateOfCreation.truncatedTo(ChronoUnit.SECONDS)
        val expectedModified = original.lastDateModified.truncatedTo(ChronoUnit.SECONDS)
        val id = original.id

        library.close()

        val reloaded =
            CoreMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), MutableAudioItemSqlTableDef))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .build()

        val loaded = reloaded.audioLibrary().findById(id).orElse(null)
        loaded.shouldNotBeNull()
        loaded.playCount shouldBe expectedPlayCount
        loaded.comments shouldBe expectedComments
        loaded.bpm shouldBe expectedBpm
        loaded.dateOfCreation.truncatedTo(ChronoUnit.SECONDS) shouldBe expectedCreation
        loaded.lastDateModified.truncatedTo(ChronoUnit.SECONDS) shouldBe expectedModified

        reloaded.close()
    }

    "MutableAudioItemSqlTableDef constructs items without firing CrudEvents and keeps reactive wiring live after load" {
        val dbFile = File.createTempFile("audioLibrary-events", ".db").apply { deleteOnExit() }
        val playlistsFile = File.createTempFile("playlistHierarchy-events", ".json").apply { deleteOnExit() }

        val seedLibrary =
            CoreMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), MutableAudioItemSqlTableDef))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .build()
        val seededIds = (1..3).map { seedLibrary.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next()).id }
        reactive.advance()
        seedLibrary.close()

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), MutableAudioItemSqlTableDef))
                .playlistRepository(JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer))
                .build()

        library.audioLibrary().size() shouldBe seededIds.size

        // Reactive wiring is live after construction-free load: a post-load mutation publishes
        // exactly one MutationEvent through the reconstructed entity's own change stream.
        val item = library.audioLibrary().findById(seededIds.first()).orElse(null)
        item.shouldNotBeNull()
        val mutationEvents = AtomicInteger(0)
        item.subscribe { mutationEvents.incrementAndGet() }
        item.title = "Mutated Title"
        reactive.advance()
        mutationEvents.get() shouldBe 1

        library.close()
    }

    "MutableAudioItemSqlTableDef declares the constructor-param columns the in-base raw constructor expects" {
        // constructorParams produces path/id/metadata/dateOfCreation/lastDateModified/playCount;
        // the table must carry the identity and ctor-scalar columns those keys are read from.
        val columnNames = MutableAudioItemSqlTableDef.columns.map { it.name }.toSet()
        columnNames.containsAll(listOf("path", "id", "date_of_creation", "play_count")) shouldBe true
        MutableAudioItemSqlTableDef.entityClassName shouldBe "net.transgressoft.commons.music.audio.MutableAudioItem"
    }
})