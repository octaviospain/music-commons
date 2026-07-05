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
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.StandardCrudEvent
import net.transgressoft.lirp.persistence.VolatileRepository
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.engine.names.WithDataTestName
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.seconds

internal class DefaultAlbumRegistryTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var registry: DefaultAlbumRegistry<AudioItem>

    beforeEach {
        repository = VolatileRepository("DefaultAlbumRegistryTest")
        registry = DefaultAlbumRegistry(repository)
    }

    afterEach {
        registry.close()
    }

    "DefaultAlbumRegistry creates album catalog when item is added to repository" {
        val artist = Artist.of("Portishead", CountryCode.UK)
        val album = AlbumDetails("Dummy", artist)
        val audioItem = files.catalogItem(artist, album, "Sour Times")

        repository.add(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe 1
            registry.findFirst("Dummy") shouldBePresent { catalog ->
                catalog.album.name shouldBe "Dummy"
                catalog.size shouldBe 1
                catalog.tracks.shouldContainOnly(audioItem)
            }
        }
    }

    "DefaultAlbumRegistry removes album catalog when last item is removed from repository" {
        val artist = Artist.of("Portishead", CountryCode.UK)
        val album = AlbumDetails("Dummy", artist)
        val audioItem = files.catalogItem(artist, album, "Sour Times")
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst("Dummy").isPresent shouldBe true }

        repository.remove(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            registry.isEmpty shouldBe true
            registry.findFirst("Dummy").shouldBeEmpty()
        }
    }

    "DefaultAlbumRegistry re-keys item to new album bucket when album changes via repository UPDATE" {
        val artist = Artist.of("Radiohead", CountryCode.UK)
        val oldAlbum = AlbumDetails("Pablo Honey", artist)
        val newAlbum = AlbumDetails("OK Computer", artist)
        val audioItem = files.catalogItem(artist, oldAlbum, "Creep")
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst("Pablo Honey").isPresent shouldBe true }

        (audioItem as MutableAudioItem).album = newAlbum
        repository.emitAsync(StandardCrudEvent.Update(audioItem, audioItem))
        reactive.advance()

        eventually(2.seconds) {
            registry.findFirst("OK Computer").isPresent shouldBe true
            registry.findFirst("Pablo Honey").shouldBeEmpty()
        }
    }

    "DefaultAlbumRegistry builds catalog from items already in repository at construction" {
        val artist = Artist.of("Massive Attack")
        val album = AlbumDetails("Mezzanine", artist)
        val paths = files.virtualAlbumAudioFiles(artist, album, size = 3..5).next()
        val items = paths.mapIndexed { idx, path -> createAudioItem(path, idx + 1, files.metadataIO) }

        items.forEach { repository.add(it) }
        registry.close()
        registry = DefaultAlbumRegistry(repository)
        reactive.advance()

        eventually(2.seconds) {
            registry.findFirst("Mezzanine") shouldBePresent { catalog ->
                catalog.album.name shouldBe "Mezzanine"
                catalog.size shouldBe items.size
                catalog.tracks.shouldContainOnly(items)
            }
        }
    }

    "DefaultAlbumRegistry emits CREATE event when first item for album is added" {
        val receivedEvents = registry.albumPublisher.collect(CREATE)

        val artist = Artist.of("Bjork", CountryCode.IS)
        val album = AlbumDetails("Homogenic", artist)
        val audioItem = files.catalogItem(artist, album, "Joga")

        repository.add(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            receivedEvents.size shouldBe 1
            receivedEvents[0].entities.values.first().album.name shouldBe "Homogenic"
        }
    }

    "DefaultAlbumRegistry emits UPDATE event when second item for same album is added" {
        val artist = Artist.of("Daft Punk", CountryCode.FR)
        val album = AlbumDetails("Random Access Memories", artist)
        val firstItem = files.catalogItem(artist, album, "Get Lucky")
        repository.add(firstItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst("Random Access Memories").isPresent shouldBe true }

        val updateEvents = registry.albumPublisher.collect(UPDATE)

        val secondItem = files.catalogItem(artist, album, "Instant Crush", track = 2)
        repository.add(secondItem)
        reactive.advance()

        eventually(2.seconds) {
            updateEvents.size shouldBe 1
            updateEvents[0].entities.values.first().size shouldBe 2
        }
    }

    "DefaultAlbumRegistry emits DELETE event when last item of album is removed" {
        val artist = Artist.of("Boards of Canada", CountryCode.UK)
        val album = AlbumDetails("Music Has the Right to Children", artist)
        val audioItem = files.catalogItem(artist, album, "Roygbiv")
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst("Music Has the Right to Children").isPresent shouldBe true }

        val deleteEvents = registry.albumPublisher.collect(DELETE)

        repository.remove(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            deleteEvents.size shouldBe 1
            registry.isEmpty shouldBe true
        }
    }

    // Distinct tag shapes that must all canonicalize into a single album bucket: the album key
    // ignores year, ignores albumArtist for compilations, and normalizes case and surrounding
    // whitespace on the album name. Each row seeds its tracks and asserts exactly one bucket forms.
    data class CanonicalizationCase(
        val scenarioName: String,
        val buildTracks: (VirtualFiles) -> List<AudioItem>
    ) : WithDataTestName {
        override fun dataTestName() = scenarioName
    }

    withData(
        CanonicalizationCase("same album name but varying year") { files ->
            val artist = Artist.of("Aphex Twin", CountryCode.UK)
            val albumName = "Selected Ambient Works"
            listOf(
                files.catalogItem(artist, AlbumDetails(albumName, artist, year = 2011), "Xtal", track = 1),
                files.catalogItem(artist, AlbumDetails(albumName, artist, year = null), "Tha", track = 2),
                files.catalogItem(artist, AlbumDetails(albumName, artist, year = 2012), "Pulsewidth", track = 3)
            )
        },
        CanonicalizationCase("compilation tracks with varying albumArtist") { files ->
            val albumName = "Cherry Moon 9"
            // 11 tracks with blank albumArtist and isCompilation = true, plus one with an explicit
            // "Various Artists" albumArtist and isCompilation = false — all one compilation album.
            val variousArtists = Artist.of("Various Artists")
            buildList {
                (1..11).forEach { idx ->
                    val compilationAlbum = AlbumDetails(albumName, Artist.UNKNOWN, isCompilation = true)
                    add(files.catalogItem(Artist.UNKNOWN, compilationAlbum, "Track $idx", track = idx.toShort()))
                }
                val bonusAlbum = AlbumDetails(albumName, variousArtists, isCompilation = false)
                add(files.catalogItem(variousArtists, bonusAlbum, "Bonus Track", track = 12))
            }
        },
        CanonicalizationCase("album names differing only in case and surrounding whitespace") { files ->
            val artist = Artist.of("Autechre", CountryCode.UK)
            listOf(
                files.catalogItem(artist, AlbumDetails("Tri Repetae", artist), "Dael", track = 1),
                files.catalogItem(artist, AlbumDetails("tri repetae", artist), "Clipper", track = 2),
                files.catalogItem(artist, AlbumDetails("  Tri Repetae ", artist), "Leterel", track = 3)
            )
        }
    ) { case ->
        case.buildTracks(files).forEach { repository.add(it) }
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe 1
        }
    }

    "DefaultAlbumRegistry derives representative year as most-frequent non-null value" {
        val artist = Artist.of("Burial", CountryCode.UK)
        val albumName = "Untrue"
        // 8 tracks with year = 2007 (majority), 1 track with null year, 1 track with year = 2008
        val majorityItems =
            (1..8).map { idx ->
                files.catalogItem(artist, AlbumDetails(albumName, artist, year = 2007), "Track $idx", track = idx.toShort())
            }
        val nullYearItem = files.catalogItem(artist, AlbumDetails(albumName, artist, year = null), "Archangel", track = 9)
        val mistagggedItem = files.catalogItem(artist, AlbumDetails(albumName, artist, year = 2008), "Ghost Hardware", track = 10)

        majorityItems.forEach { repository.add(it) }
        repository.add(nullYearItem)
        repository.add(mistagggedItem)
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe 1
            registry.findFirst(albumName) shouldBePresent { bucket ->
                bucket.album.year shouldBe 2007
            }
        }
    }

    "DefaultAlbumRegistry emits UPDATE (not DELETE then CREATE) when year field changes on a track" {
        val artist = Artist.of("Actress", CountryCode.UK)
        val albumName = "Splazsh"
        val initialAlbum = AlbumDetails(albumName, artist, year = 2010)
        val audioItem = files.catalogItem(artist, initialAlbum, "Hubble")
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst(albumName).isPresent shouldBe true }

        val updateEvents = registry.albumPublisher.collect(UPDATE)
        val deleteEvents = registry.albumPublisher.collect(DELETE)

        // Emit a corrected clone so the projection detects a reference change and re-runs the
        // value-transform. A clone with a new reference triggers repositionInBucket to rebuild
        // the bucket and notify listeners; an in-place mutation on the same reference would be
        // silently skipped by the projection's position-and-reference no-op guard.
        val correctedAlbum = AlbumDetails(albumName, artist, year = null)
        val correctedItem = (audioItem as MutableAudioItem).clone().also { it.album = correctedAlbum }
        repository.emitAsync(StandardCrudEvent.Update(correctedItem, audioItem))

        eventually(2.seconds) {
            reactive.advance()
            updateEvents.size shouldBe 1
            deleteEvents.size shouldBe 0
            registry.findFirst(albumName) shouldBePresent { bucket ->
                bucket.album.year shouldBe null
            }
        }
    }

    "DefaultAlbumRegistry findById resolves bucket when called with non-canonical AlbumDetails" {
        val artist = Artist.of("Gas", CountryCode.DE)
        val albumName = "Nah und Fern"
        val canonicalAlbum = AlbumDetails(albumName, artist)
        val audioItem = files.catalogItem(artist, canonicalAlbum, "Pop 1")
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst(albumName).isPresent shouldBe true }

        // Non-canonical: same name + artist but with year populated
        val nonCanonicalAlbum = AlbumDetails(albumName, artist, year = 2000)

        eventually(2.seconds) {
            registry.findById(nonCanonicalAlbum) shouldBePresent { bucket ->
                bucket.album.name shouldBe albumName
            }
        }
    }

    "DefaultAlbumRegistry getRandomAudioItemsFromAlbum returns items when called with a non-canonical AlbumDetails" {
        val artist = Artist.of("Andy Stott", CountryCode.UK)
        val albumName = "Luxury Problems"
        // Two tracks of the same canonical album: one with year populated, one without
        val itemWithYear = files.catalogItem(artist, AlbumDetails(albumName, artist, year = 2012), "Numb")
        val itemWithoutYear = files.catalogItem(artist, AlbumDetails(albumName, artist, year = null), "Lost and Found", track = 2)
        repository.add(itemWithYear)
        repository.add(itemWithoutYear)
        reactive.advance()
        eventually(2.seconds) { registry.size() shouldBe 1 }

        // Access getRandomAudioItemsFromAlbum via a TestAudioLibrary backed by the same repository
        val library = TestAudioLibrary(repository, files.metadataIO)

        // Call with a populated-year AlbumDetails (non-canonical) — must resolve from the merged bucket
        val nonCanonicalAlbum = AlbumDetails(albumName, artist, year = 2012)
        eventually(2.seconds) {
            library.getRandomAudioItemsFromAlbum(nonCanonicalAlbum, 10).shouldNotBeEmpty()
        }

        library.close()
    }

    // Cross-bucket ordering: buckets sort by album name (blank last), then artist, then year.
    // Each row seeds its tracks (in a deliberately non-sorted add order) before the registry is
    // built so the ordered projection initializes from the fully-populated repository — the ordered
    // TreeMap comparator reads only already-cached values during the seed, avoiding a lirp edge case
    // on the incremental-insert path.
    data class OrderingCase(
        val scenarioName: String,
        val buildTracks: (VirtualFiles) -> List<AudioItem>,
        val expectedOrder: List<String>
    ) : WithDataTestName {
        override fun dataTestName() = scenarioName
    }

    withData(
        OrderingCase(
            "buckets ordered by album name then artist then year",
            buildTracks = { files ->
                val artistA = Artist.of("Autechre")
                val artistB = Artist.of("Bjork", CountryCode.IS)
                val artistC = Artist.of("Can")
                // Returned in reverse of the expected order to confirm ordering is not insertion-order.
                listOf(
                    files.catalogItem(artistC, AlbumDetails("Tago Mago", artistC, year = 1971), "Paperhouse"),
                    files.catalogItem(artistB, AlbumDetails("Homogenic", artistB, year = 1997), "Joga"),
                    files.catalogItem(artistA, AlbumDetails("Confield", artistA, year = 2001), "VI Scose Poise")
                )
            },
            expectedOrder = listOf("Confield", "Homogenic", "Tago Mago")
        ),
        OrderingCase(
            "blank name bucket sorts last",
            buildTracks = { files ->
                val artist = Artist.of("Unknown Artist")
                listOf(
                    files.catalogItem(Artist.UNKNOWN, AlbumDetails("", Artist.UNKNOWN), "Untitled"),
                    files.catalogItem(artist, AlbumDetails("Arca", artist), "Piel")
                )
            },
            expectedOrder = listOf("Arca", "")
        ),
        OrderingCase(
            "two named buckets ordered alphabetically",
            buildTracks = { files ->
                val artistA = Artist.of("Actress", CountryCode.UK)
                val artistP = Artist.of("Portishead", CountryCode.UK)
                // "Dummy" < "Splazsh" alphabetically, so Dummy must come first regardless of add order.
                listOf(
                    files.catalogItem(artistA, AlbumDetails("Splazsh", artistA, year = 2010), "Hubble"),
                    files.catalogItem(artistP, AlbumDetails("Dummy", artistP, year = 1994), "Sour Times")
                )
            },
            expectedOrder = listOf("Dummy", "Splazsh")
        )
    ) { case ->
        registry = seededRegistry(repository, registry, case.buildTracks(files), close = { it.close() }) { DefaultAlbumRegistry(repository) }
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe case.expectedOrder.size
            registry.orderedValues().map { it.album.name } shouldBe case.expectedOrder
        }
    }

    "DefaultAlbumRegistry orderedValues bucket preserves disc then track order within album" {
        val artist = Artist.of("Pink Floyd")
        val album = AlbumDetails("The Wall", artist, year = 1979)

        val track1Disc1 = files.catalogItem(artist, album, "In the Flesh?", track = 1, disc = 1)
        val track1Disc2 = files.catalogItem(artist, album, "Hey You", track = 1, disc = 2)
        val track2Disc1 = files.catalogItem(artist, album, "The Thin Ice", track = 2, disc = 1)

        // Add out of order
        repository.add(track1Disc2)
        repository.add(track2Disc1)
        repository.add(track1Disc1)
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe 1
            val bucket = registry.orderedValues().first()
            // disc 1 tracks before disc 2; within each disc, ascending track number
            bucket.tracks.map { it.trackNumber to it.discNumber } shouldBe
                listOf(1.toShort() to 1.toShort(), 2.toShort() to 1.toShort(), 1.toShort() to 2.toShort())
        }
    }
})