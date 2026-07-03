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

package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.MutableAudioItemTestBridge.createAudioItem
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
import kotlin.time.Duration.Companion.seconds

/**
 * Conservation (completeness) tests for the three audio-library projections.
 *
 * These guard a property none of the per-bucket tests cover: every audio item in the library is
 * accounted for across a projection's buckets, with nothing silently dropped.
 *
 * - Album is single-key, so its buckets form a partition: the union of all bucket tracks equals
 *   the library exactly and the total member count matches the library size.
 * - Genre and artist are multi-key (a track may carry several genres; a track may involve several
 *   artists), so their buckets form a cover: the deduplicated union of all bucket members equals
 *   the library, and a track with no genre still surfaces in the [Genre.None] bucket rather than
 *   being dropped.
 *
 * Items are seeded into the repository before the registries are constructed so each projection
 * initializes from the fully-populated repository, which keeps the ordered projections off the
 * incremental-insert path.
 */
internal class AudioLibraryProjectionConservationTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    lateinit var repository: VolatileRepository<Int, AudioItem>
    val closeActions = mutableListOf<() -> Unit>()

    beforeEach {
        repository = VolatileRepository("AudioLibraryProjectionConservationTest")
    }

    afterEach {
        closeActions.forEach { runCatching { it() } }
        closeActions.clear()
    }

    fun item(
        artist: Artist,
        albumDetails: AlbumDetails,
        itemGenres: Set<Genre>,
        trackTitle: String,
        track: Short
    ): AudioItem =
        createAudioItem(
            files.virtualAudioFile {
                this.artist = artist
                this.album = albumDetails
                genres = itemGenres
                title = trackTitle
                trackNumber = track
                discNumber = 1
            }.next(),
            files.metadataIO
        )

    // Seeds every item into the repository, then builds the three projection registries from that
    // fully-populated repository. Returns them registered for teardown.
    fun projectionsOver(
        items: List<AudioItem>
    ): Triple<DefaultArtistCatalogRegistry<AudioItem>, DefaultAlbumRegistry<AudioItem>, DefaultGenreIndexRegistry<AudioItem>> {
        items.forEach { repository.add(it) }
        val artistRegistry = DefaultArtistCatalogRegistry(repository)
        val albumRegistry = DefaultAlbumRegistry(repository)
        val genreRegistry = DefaultGenreIndexRegistry(repository)
        closeActions += { artistRegistry.close() }
        closeActions += { albumRegistry.close() }
        closeActions += { genreRegistry.close() }
        return Triple(artistRegistry, albumRegistry, genreRegistry)
    }

    fun albumMembers(registry: DefaultAlbumRegistry<AudioItem>): List<AudioItem> =
        registry.orderedValues().flatMap { it.tracks }

    fun genreMembers(registry: DefaultGenreIndexRegistry<AudioItem>): List<AudioItem> =
        registry.orderedValues().flatMap { it.tracks }

    fun artistMembers(registry: DefaultArtistCatalogRegistry<AudioItem>): List<AudioItem> =
        buildList { registry.forEach { catalog -> catalog.albums.forEach { addAll(it.tracks) } } }

    "album, genre and artist projections together account for every library item including edge cases" {
        val moby = Artist.of("Moby")
        val bjork = Artist.of("Bjork")
        val aphex = Artist.of("Aphex Twin")
        val unknown = Artist.of("Unknown Artist")

        // Multi-genre track: must appear in two genre buckets.
        val multiGenre = item(moby, AlbumDetails("Play", moby), setOf(Electronic, Rock), "Porcelain", 1)
        // Dual-artist track (track artist != album artist): must appear in two artist catalogs.
        val dualArtist = item(moby, AlbumDetails("Collab", bjork), setOf(Electronic), "Together", 1)
        // Untagged track: must surface in the Genre.None bucket, not be dropped.
        val untagged = item(aphex, AlbumDetails("Drukqs", aphex), emptySet(), "Avril 14th", 1)
        // Blank-album track: must still occupy exactly one album bucket.
        val blankAlbum = item(unknown, AlbumDetails("", Artist.UNKNOWN), setOf(Rock), "Untitled", 1)
        // A plain track sharing nothing with the others.
        val plain = item(bjork, AlbumDetails("Homogenic", bjork), setOf(Alternative), "Joga", 1)

        val allItems = listOf(multiGenre, dualArtist, untagged, blankAlbum, plain)
        val (artistRegistry, albumRegistry, genreRegistry) = projectionsOver(allItems)
        reactive.advance()

        eventually(2.seconds) {
            // Album: partition — union equals the library and every item lands in exactly one bucket.
            albumMembers(albumRegistry) shouldHaveSize allItems.size
            albumMembers(albumRegistry) shouldContainExactlyInAnyOrder allItems

            // Genre: cover — deduped union equals the library; the untagged track is in Genre.None.
            genreMembers(genreRegistry).toSet() shouldContainExactlyInAnyOrder allItems
            genreRegistry.findById(Genre.None).get().tracks shouldContainExactlyInAnyOrder listOf(untagged)
            genreRegistry.findById(Electronic).get().tracks.toSet() shouldContainExactlyInAnyOrder setOf(multiGenre, dualArtist)
            genreRegistry.findById(Rock).get().tracks.toSet() shouldContainExactlyInAnyOrder setOf(multiGenre, blankAlbum)

            // Artist: cover — deduped union equals the library; the dual-artist track is in both catalogs.
            artistMembers(artistRegistry).toSet() shouldContainExactlyInAnyOrder allItems
            artistRegistry.findById(moby).get().albumAudioItems("Collab") shouldContainExactlyInAnyOrder listOf(dualArtist)
            artistRegistry.findById(bjork).get().albumAudioItems("Collab") shouldContainExactlyInAnyOrder listOf(dualArtist)
        }
    }

    "projections stay complete after an item is removed and after one is added to existing buckets" {
        val artist = Artist.of("Radiohead")
        val album = AlbumDetails("OK Computer", artist)
        val seeded =
            listOf(
                item(artist, album, setOf(Alternative), "Airbag", 1),
                item(artist, album, setOf(Alternative), "Paranoid Android", 2),
                item(Artist.of("Portishead"), AlbumDetails("Dummy", Artist.of("Portishead")), setOf(Electronic), "Roads", 1)
            )
        val (artistRegistry, albumRegistry, genreRegistry) = projectionsOver(seeded)
        reactive.advance()

        eventually(2.seconds) {
            albumMembers(albumRegistry) shouldContainExactlyInAnyOrder seeded
            genreMembers(genreRegistry).toSet() shouldContainExactlyInAnyOrder seeded
            artistMembers(artistRegistry).toSet() shouldContainExactlyInAnyOrder seeded
        }

        // Remove a track: conservation must hold over the remaining items.
        repository.remove(seeded[2])
        reactive.advance()
        val afterRemoval = seeded.take(2)

        eventually(2.seconds) {
            albumMembers(albumRegistry) shouldContainExactlyInAnyOrder afterRemoval
            genreMembers(genreRegistry).toSet() shouldContainExactlyInAnyOrder afterRemoval
            artistMembers(artistRegistry).toSet() shouldContainExactlyInAnyOrder afterRemoval
        }

        // Add a track that reuses an existing artist, album and genre bucket (no new bucket created):
        // conservation must grow to include it.
        val added = item(artist, album, setOf(Alternative), "Karma Police", 3)
        repository.add(added)
        reactive.advance()
        val afterAdd = afterRemoval + added

        eventually(2.seconds) {
            albumMembers(albumRegistry) shouldHaveSize afterAdd.size
            albumMembers(albumRegistry) shouldContainExactlyInAnyOrder afterAdd
            genreMembers(genreRegistry).toSet() shouldContainExactlyInAnyOrder afterAdd
            artistMembers(artistRegistry).toSet() shouldContainExactlyInAnyOrder afterAdd
        }
    }

    "randomly generated libraries are fully accounted for across all three projections" {
        val artists = listOf(Artist.of("A"), Artist.of("B"), Artist.of("C"))
        val albumNames = listOf("Alpha", "Beta", "Gamma", "")
        val genreSets = listOf(setOf(Rock), setOf(Electronic, Jazz), emptySet(), setOf(Blues))

        // Small hand-rolled generation loop (seed-before-registry per iteration) rather than checkAll,
        // so each iteration gets a fresh repository + registries and deterministic teardown.
        repeat(15) { iteration ->
            val iterationRepository = VolatileRepository<Int, AudioItem>("conservation-property-$iteration")
            val size = Arb.int(1..10).next()
            val items =
                (1..size).map { index ->
                    val artist = Arb.element(artists).next()
                    val albumName = Arb.element(albumNames).next()
                    val albumArtist = if (albumName.isBlank()) Artist.UNKNOWN else artist
                    item(artist, AlbumDetails(albumName, albumArtist), Arb.element(genreSets).next(), "Track $index", index.toShort())
                }
            items.forEach { iterationRepository.add(it) }

            val artistRegistry = DefaultArtistCatalogRegistry(iterationRepository)
            val albumRegistry = DefaultAlbumRegistry(iterationRepository)
            val genreRegistry = DefaultGenreIndexRegistry(iterationRepository)
            reactive.advance()

            try {
                eventually(2.seconds) {
                    // Album partition: union equals the library and item count is conserved exactly.
                    albumMembers(albumRegistry) shouldHaveSize items.size
                    albumMembers(albumRegistry) shouldContainExactlyInAnyOrder items
                    // Genre cover: deduped union equals the library (untagged items land in Genre.None).
                    genreMembers(genreRegistry).toSet() shouldContainExactlyInAnyOrder items
                    // Artist cover: deduped union equals the library.
                    artistMembers(artistRegistry).toSet() shouldContainExactlyInAnyOrder items
                }
            } finally {
                artistRegistry.close()
                albumRegistry.close()
                genreRegistry.close()
            }
        }
    }
})