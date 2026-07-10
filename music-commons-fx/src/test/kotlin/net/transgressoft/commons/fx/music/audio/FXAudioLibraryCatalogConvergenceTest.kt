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

import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.ReactiveScopeSerialization
import net.transgressoft.commons.music.testing.registryIsolation
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Convergence regression test for the JavaFX artist-catalog projection under a compilation-style,
 * involved-artist-heavy import on the real reactive dispatcher.
 *
 * A shared "Various Artists" album artist is re-projected once per track, so its catalog is
 * update-churned as many times as there are tracks, each churn producing a fresh, content-unequal
 * `FXArtistCatalog`. Combined with the debounced `Platform.runLater` reconcile running against the
 * production `Dispatchers.Default` scope, this is the scenario in which a lost catalog-refresh re-arm
 * would leave `artistCatalogsProperty` permanently missing the final track(s) of the churned catalog.
 *
 * The spec keeps `ReactiveScope`'s production dispatchers via [ReactiveScopeSerialization] (rather
 * than swapping in a deterministic test dispatcher, which would collapse producer and consumer onto
 * one thread and hide the interleaving) and asserts that the public property converges to the same
 * per-artist coverage as the registry projection.
 */
@ExperimentalCoroutinesApi
internal class FXAudioLibraryCatalogConvergenceTest : StringSpec({

    registryIsolation()
    extension(ReactiveScopeSerialization)
    val files = virtualFiles()

    var nextId = 1

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        FxToolkit.cleanupStages()
    }

    fun FXAudioLibrary.addItem(artist: Artist, album: AlbumDetails, trackTitle: String, track: Short): FXAudioItem {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                title = trackTitle
                trackNumber = track
                discNumber = 1
            }.next()
        return FXAudioItemTestBridge.createFxAudioItem(path, nextId++, files.metadataIO).also { add(it) }
    }

    fun ObservableArtistCatalog.itemIds(): Set<Int> = albums.flatMap { it.tracks }.map { it.id }.toSet()

    "artistCatalogsProperty converges to full per-artist coverage under a compilation-style involved-artist import" {
        val trackCount = 40

        repeat(6) { iteration ->
            FXAudioLibrary(VolatileRepository("CatalogConvergenceFx-$iteration")).use { library ->
                val variousArtists = Artist.of("Various Artists")
                val compilation = AlbumDetails("Compilations", variousArtists)

                // Each track has a distinct primary artist plus the shared "Various Artists" album artist,
                // so every track lands in two catalogs and the shared catalog is churned once per track.
                val expectedByArtist = linkedMapOf<Artist, MutableSet<Int>>()
                for (index in 1..trackCount) {
                    val trackArtist = Artist.of("Artist $index")
                    val item = library.addItem(trackArtist, compilation, "Track $index", index.toShort())
                    expectedByArtist.getOrPut(trackArtist) { mutableSetOf() }.add(item.id)
                    expectedByArtist.getOrPut(variousArtists) { mutableSetOf() }.add(item.id)
                }

                eventually(5.seconds) {
                    WaitForAsyncUtils.waitForFxEvents()

                    val catalogs: Set<ObservableArtistCatalog> = library.artistCatalogsProperty
                    val catalogsByArtist = catalogs.associateBy { it.artist }

                    // The public property exposes exactly one catalog per involved artist...
                    catalogsByArtist.keys shouldContainExactlyInAnyOrder expectedByArtist.keys

                    expectedByArtist.forEach { (artist, expectedIds) ->
                        // ...the churned "Various Artists" catalog is not left missing its last track(s)...
                        catalogsByArtist.getValue(artist).itemIds() shouldContainExactlyInAnyOrder expectedIds
                        // ...and the public property agrees with the registry projection.
                        library.getArtistCatalog(artist).get().itemIds() shouldContainExactlyInAnyOrder expectedIds
                    }
                }
            }
        }
    }
})