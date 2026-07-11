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
import net.transgressoft.commons.fx.music.audio.ObservableArtistCatalog
import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.Artist.Companion.of
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.ReactiveScopeSerialization
import net.transgressoft.lirp.persistence.sql.SqliteRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Convergence regression for the SQLite-backed JavaFX artist-catalog projection under the iTunes
 * import's add-then-mutate flow.
 *
 * The import adds each item to the repository from its file tag and only afterward mutates it in
 * place with the richer metadata whose title contributes additional involved artists. On the
 * SQLite-backed repository, under the production reactive dispatcher, the re-keying update stream
 * must not drop the newly involved artists: every artist an item ends up involved with — including
 * one that exists only in the mutated title — must expose a retrievable catalog.
 */
@ExperimentalCoroutinesApi
internal class FXArtistCatalogSqliteConvergenceTest : StringSpec({

    extension(ReactiveScopeSerialization)
    val files = virtualFiles()

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        FxToolkit.cleanupStages()
    }

    fun ObservableArtistCatalog.itemIds(): Set<Int> = albums.flatMap { it.tracks }.map { it.id }.toSet()

    "getArtistCatalog resolves every title-guest after an add-then-mutate SQLite import at scale" {
        val trackCount = 120
        val dbFile = File.createTempFile("fxArtistCatalogSql", ".db").apply { deleteOnExit() }

        val library =
            FXMusicLibrary.builder()
                .audioRepository(SqliteRepository.fileBacked(dbFile.toPath(), FXAudioItemSqlTableDef))
                .metadataIO(files.metadataIO)
                .build()
        val repository = library.audioLibrary()
        try {
            val variousArtists = of("Various Artists")
            val compilation = AlbumDetails("Compilations", variousArtists)

            val expectedGuestIds = linkedMapOf<Artist, Int>()
            for (index in 1..trackCount) {
                val path =
                    files.virtualAudioFile {
                        this.artist = of("Primary $index")
                        this.album = compilation
                        title = "Song $index"
                        trackNumber = index.toShort()
                        discNumber = 1
                    }.next()
                // Add from the plain-title file, then mutate in place to introduce a unique title-only
                // guest, exactly as the iTunes import applies its metadata after the initial add.
                val item = repository.createFromFile(path)
                // Mirror applyItunesMetadata: title, artist and album are all set within one mutate, so
                // each setter re-runs syncArtistsInvolved (clear + repopulate) and the projection must
                // absorb several key-set churns per item before settling on the final involved set.
                item.mutate {
                    title = "Song ${item.id} feat Guest ${item.id}"
                    artist = of("Primary ${item.id}")
                    album = compilation
                }
                expectedGuestIds[of("Guest ${item.id}")] = item.id
            }

            eventually(15.seconds) {
                WaitForAsyncUtils.waitForFxEvents()
                expectedGuestIds.forEach { (guest, id) ->
                    repository.getArtistCatalog(guest).shouldBePresent { it.itemIds() shouldContain id }
                }
            }
        } finally {
            library.close()
        }
    }
})