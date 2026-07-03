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
import net.transgressoft.commons.music.audio.Alternative
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.Blues
import net.transgressoft.commons.music.audio.Electronic
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.Jazz
import net.transgressoft.commons.music.audio.Rock
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Conservation (completeness) tests for the JavaFX-observable audio-library projections.
 *
 * These assert that every item in the library is accounted for across the observable projection
 * properties, mirroring the core registry guarantees on the FX surface consumers bind to:
 *
 * - `albumsProperty` is single-key, so its buckets partition the library: the union of all bucket
 *   tracks equals the library exactly and the total member count matches the library size.
 * - `genreIndexesProperty` and `artistCatalogsProperty` are multi-key, so their buckets cover the
 *   library: the deduplicated union of all bucket members equals the library, an untagged track
 *   surfaces in the [Genre.None] bucket, and a dual-artist track surfaces in both catalogs.
 *
 * Items are compared by id so the assertions are independent of observable-wrapper identity.
 */
@ExperimentalCoroutinesApi
internal class ObservableAudioLibraryProjectionConservationTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    var nextId = 1

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        FxToolkit.cleanupStages()
    }

    fun FXAudioLibrary.addItem(
        artist: Artist,
        albumDetails: AlbumDetails,
        itemGenres: Set<Genre>,
        trackTitle: String,
        track: Short
    ): FXAudioItem {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = albumDetails
                genres = itemGenres
                title = trackTitle
                trackNumber = track
                discNumber = 1
            }.next()
        return FXAudioItemTestBridge.createFxAudioItem(path, nextId++, files.metadataIO).also { add(it) }
    }

    // The properties are bound to plain List/Set locals first because JavaFX's ObservableValue
    // exposes its own flatMap/map, which would otherwise shadow the Kotlin collection operators.
    fun ObservableAudioLibrary.albumMemberIds(): List<Int> {
        val albums: List<ObservableAlbum> = albumsProperty
        return albums.flatMap { it.tracks }.map { it.id }
    }

    fun ObservableAudioLibrary.genreMemberIds(): Set<Int> {
        val genreIndexes: List<ObservableGenreIndex> = genreIndexesProperty
        return genreIndexes.flatMap { it.tracks }.map { it.id }.toSet()
    }

    fun ObservableAudioLibrary.artistMemberIds(): Set<Int> {
        val catalogs: Set<ObservableArtistCatalog> = artistCatalogsProperty
        return catalogs.flatMap { catalog -> catalog.albums.flatMap { it.tracks } }.map { it.id }.toSet()
    }

    "albumsProperty, genreIndexesProperty and artistCatalogsProperty account for every item including edge cases" {
        FXAudioLibrary(VolatileRepository("ConservationEdgeCasesFx")).use { library ->
            val moby = Artist.of("Moby")
            val bjork = Artist.of("Bjork")
            val aphex = Artist.of("Aphex Twin")
            val unknown = Artist.of("Unknown Artist")

            val multiGenre = library.addItem(moby, AlbumDetails("Play", moby), setOf(Electronic, Rock), "Porcelain", 1)
            val dualArtist = library.addItem(moby, AlbumDetails("Collab", bjork), setOf(Electronic), "Together", 1)
            val untagged = library.addItem(aphex, AlbumDetails("Drukqs", aphex), emptySet(), "Avril 14th", 1)
            val blankAlbum = library.addItem(unknown, AlbumDetails("", Artist.UNKNOWN), setOf(Rock), "Untitled", 1)
            val plain = library.addItem(bjork, AlbumDetails("Homogenic", bjork), setOf(Alternative), "Joga", 1)

            val expectedIds = listOf(multiGenre, dualArtist, untagged, blankAlbum, plain).map { it.id }.toSet()

            eventually(2.seconds) {
                reactive.advance()
                WaitForAsyncUtils.waitForFxEvents()

                // Album: partition — union equals the library and every item lands in exactly one bucket.
                library.albumMemberIds() shouldHaveSize expectedIds.size
                library.albumMemberIds().toSet() shouldContainExactlyInAnyOrder expectedIds

                // Genre: cover — deduped union equals the library; the untagged track is in Genre.None.
                library.genreMemberIds() shouldContainExactlyInAnyOrder expectedIds
                library.getGenreIndex(Genre.None).get().tracks.map { it.id } shouldContainExactlyInAnyOrder listOf(untagged.id)

                // Artist: cover — deduped union equals the library; the dual-artist track is in both catalogs.
                library.artistMemberIds() shouldContainExactlyInAnyOrder expectedIds
                library.getArtistCatalog(moby).get().albumAudioItems("Collab").map { it.id } shouldContainExactlyInAnyOrder listOf(dualArtist.id)
                library.getArtistCatalog(bjork).get().albumAudioItems("Collab").map { it.id } shouldContainExactlyInAnyOrder listOf(dualArtist.id)
            }
        }
    }

    "observable projections stay complete after an item is removed" {
        FXAudioLibrary(VolatileRepository("ConservationRemovalFx")).use { library ->
            val radiohead = Artist.of("Radiohead")
            val album = AlbumDetails("OK Computer", radiohead)
            val airbag = library.addItem(radiohead, album, setOf(Alternative), "Airbag", 1)
            val paranoid = library.addItem(radiohead, album, setOf(Alternative), "Paranoid Android", 2)
            val roads = library.addItem(Artist.of("Portishead"), AlbumDetails("Dummy", Artist.of("Portishead")), setOf(Electronic), "Roads", 1)

            val allIds = listOf(airbag, paranoid, roads).map { it.id }.toSet()
            eventually(2.seconds) {
                reactive.advance()
                WaitForAsyncUtils.waitForFxEvents()
                library.albumMemberIds().toSet() shouldContainExactlyInAnyOrder allIds
                library.genreMemberIds() shouldContainExactlyInAnyOrder allIds
                library.artistMemberIds() shouldContainExactlyInAnyOrder allIds
            }

            library.remove(roads)
            val remainingIds = listOf(airbag, paranoid).map { it.id }.toSet()

            eventually(2.seconds) {
                reactive.advance()
                WaitForAsyncUtils.waitForFxEvents()
                library.albumMemberIds() shouldHaveSize remainingIds.size
                library.albumMemberIds().toSet() shouldContainExactlyInAnyOrder remainingIds
                library.genreMemberIds() shouldContainExactlyInAnyOrder remainingIds
                library.artistMemberIds() shouldContainExactlyInAnyOrder remainingIds
            }
        }
    }

    "randomly generated libraries are fully accounted for across all observable projections" {
        val artists = listOf(Artist.of("A"), Artist.of("B"), Artist.of("C"))
        val albumNames = listOf("Alpha", "Beta", "Gamma", "")
        val genreSets = listOf(setOf(Rock), setOf(Electronic, Jazz), emptySet(), setOf(Blues))

        repeat(8) { iteration ->
            FXAudioLibrary(VolatileRepository("ConservationPropertyFx-$iteration")).use { library ->
                val size = Arb.int(1..8).next()
                val expectedIds =
                    (1..size).map { index ->
                        val artist = Arb.element(artists).next()
                        val albumName = Arb.element(albumNames).next()
                        val albumArtist = if (albumName.isBlank()) Artist.UNKNOWN else artist
                        library.addItem(artist, AlbumDetails(albumName, albumArtist), Arb.element(genreSets).next(), "Track $index", index.toShort())
                    }.map { it.id }.toSet()

                eventually(2.seconds) {
                    // Drain the shared test dispatcher inside the retry loop before pumping the FX
                    // thread, so each fresh per-iteration library settles reliably.
                    reactive.advance()
                    WaitForAsyncUtils.waitForFxEvents()
                    // Album partition: union equals the library and item count is conserved exactly.
                    library.albumMemberIds() shouldHaveSize expectedIds.size
                    library.albumMemberIds().toSet() shouldContainExactlyInAnyOrder expectedIds
                    // Genre and artist cover: deduped union equals the library.
                    library.genreMemberIds() shouldContainExactlyInAnyOrder expectedIds
                    library.artistMemberIds() shouldContainExactlyInAnyOrder expectedIds
                }
            }
        }
    }
})