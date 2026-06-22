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

package net.transgressoft.commons.persistence.fx.music.audio

import net.transgressoft.commons.fx.music.FXMusicLibrary
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.persistence.sql.SqliteRepository
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@DisplayName("FXAudioItemSqlTableDef")
internal class FXAudioItemSqlTableDefTest : StringSpec({

    val reactive = reactiveScope()

    beforeSpec { FxToolkit.registerPrimaryStage() }

    "FXAudioItemSqlTableDef round-trips an FX library through SQLite preserving per-row scalar fidelity" {
        val dbFile = tempfile("fxAudioLibrary-sql", ".db").apply { deleteOnExit() }

        val library =
            FXMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), FXAudioItemSqlTableDef))
                .build()

        val originals = (1..4).map { library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next()) }
        val byId = originals.associateBy { it.id }
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        library.close()

        val reloadedLibrary =
            FXMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), FXAudioItemSqlTableDef))
                .build()

        reloadedLibrary.audioLibrary().size() shouldBe originals.size

        reloadedLibrary.audioLibrary().forEach { loaded: ObservableAudioItem ->
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

    "FXAudioItemSqlTableDef constructs items without firing CrudEvents and keeps reactive wiring live after load" {
        val dbFile = tempfile("fxAudioLibrary-events", ".db").apply { deleteOnExit() }

        val seedLibrary =
            FXMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), FXAudioItemSqlTableDef))
                .build()
        val seededIds = (1..3).map { seedLibrary.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next()).id }
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()
        seedLibrary.close()

        val library =
            FXMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), FXAudioItemSqlTableDef))
                .build()

        library.audioLibrary().size() shouldBe seededIds.size

        val item = library.audioLibrary().findById(seededIds.first()).orElse(null)
        item.shouldNotBeNull()
        val mutationEvents = AtomicInteger(0)
        item.subscribe { mutationEvents.incrementAndGet() }
        item.title = "Mutated Title"
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()
        mutationEvents.get() shouldBe 1

        library.close()
    }

    "FXAudioItemSqlTableDef declares the constructor-param columns the in-base raw constructor expects" {
        val columnNames = FXAudioItemSqlTableDef.columns.map { it.name }.toSet()
        columnNames.containsAll(listOf("path", "id", "date_of_creation", "play_count")) shouldBe true
        FXAudioItemSqlTableDef.entityClassName shouldBe "net.transgressoft.commons.fx.music.audio.FXAudioItem"
    }
})