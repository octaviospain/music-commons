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
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.StandardCrudEvent
import net.transgressoft.lirp.persistence.VolatileRepository
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
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
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Sour Times"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

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
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Sour Times"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
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
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = oldAlbum
                    title = "Creep"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
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
        val receivedEvents = mutableListOf<CrudEvent<AlbumDetails, Album<AudioItem>>>()
        registry.albumPublisher.subscribe(CREATE) { receivedEvents.add(it) }

        val artist = Artist.of("Bjork", CountryCode.IS)
        val album = AlbumDetails("Homogenic", artist)
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Joga"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

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
        val firstItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Get Lucky"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        repository.add(firstItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst("Random Access Memories").isPresent shouldBe true }

        val updateEvents = mutableListOf<CrudEvent<AlbumDetails, Album<AudioItem>>>()
        registry.albumPublisher.subscribe(UPDATE) { updateEvents.add(it) }

        val secondItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Instant Crush"
                    trackNumber = 2
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
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
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Roygbiv"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst("Music Has the Right to Children").isPresent shouldBe true }

        val deleteEvents = mutableListOf<CrudEvent<AlbumDetails, Album<AudioItem>>>()
        registry.albumPublisher.subscribe(DELETE) { deleteEvents.add(it) }

        repository.remove(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            deleteEvents.size shouldBe 1
            registry.isEmpty shouldBe true
        }
    }

    "DefaultAlbumRegistry merges tracks with same album name but varying year into single bucket" {
        val artist = Artist.of("Aphex Twin", CountryCode.UK)
        val albumName = "Selected Ambient Works"
        val item1 =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails(albumName, artist, year = 2011)
                    title = "Xtal"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val item2 =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails(albumName, artist, year = null)
                    title = "Tha"
                    trackNumber = 2
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val item3 =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails(albumName, artist, year = 2012)
                    title = "Pulsewidth"
                    trackNumber = 3
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

        repository.add(item1)
        repository.add(item2)
        repository.add(item3)
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe 1
        }
    }

    "DefaultAlbumRegistry merges compilation tracks with varying albumArtist into single bucket" {
        val albumName = "Cherry Moon 9"
        // 11 tracks with blank albumArtist and isCompilation = true
        val compilationItems =
            (1..11).map { idx ->
                createAudioItem(
                    files.virtualAudioFile {
                        this.album = AlbumDetails(albumName, Artist.UNKNOWN, isCompilation = true)
                        title = "Track $idx"
                        trackNumber = idx.toShort()
                        discNumber = 1
                    }.next(),
                    files.metadataIO
                )
            }
        // 1 track with albumArtist = "Various Artists" and isCompilation = false
        val variousArtistsItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.album = AlbumDetails(albumName, Artist.of("Various Artists"), isCompilation = false)
                    title = "Bonus Track"
                    trackNumber = 12
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

        compilationItems.forEach { repository.add(it) }
        repository.add(variousArtistsItem)
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
                createAudioItem(
                    files.virtualAudioFile {
                        this.artist = artist
                        this.album = AlbumDetails(albumName, artist, year = 2007)
                        title = "Track $idx"
                        trackNumber = idx.toShort()
                        discNumber = 1
                    }.next(),
                    files.metadataIO
                )
            }
        val nullYearItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails(albumName, artist, year = null)
                    title = "Archangel"
                    trackNumber = 9
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val mistagggedItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails(albumName, artist, year = 2008)
                    title = "Ghost Hardware"
                    trackNumber = 10
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

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
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = initialAlbum
                    title = "Hubble"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst(albumName).isPresent shouldBe true }

        val updateEvents = mutableListOf<CrudEvent<AlbumDetails, Album<AudioItem>>>()
        val deleteEvents = mutableListOf<CrudEvent<AlbumDetails, Album<AudioItem>>>()
        registry.albumPublisher.subscribe(UPDATE) { updateEvents.add(it) }
        registry.albumPublisher.subscribe(DELETE) { deleteEvents.add(it) }

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
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = canonicalAlbum
                    title = "Pop 1"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
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

    "DefaultAlbumRegistry merges album names differing only in case and surrounding whitespace" {
        val artist = Artist.of("Autechre", CountryCode.UK)
        val item1 =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails("Tri Repetae", artist)
                    title = "Dael"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val item2 =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails("tri repetae", artist)
                    title = "Clipper"
                    trackNumber = 2
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val item3 =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails("  Tri Repetae ", artist)
                    title = "Leterel"
                    trackNumber = 3
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

        repository.add(item1)
        repository.add(item2)
        repository.add(item3)
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe 1
        }
    }

    "DefaultAlbumRegistry getRandomAudioItemsFromAlbum returns items when called with a non-canonical AlbumDetails" {
        val artist = Artist.of("Andy Stott", CountryCode.UK)
        val albumName = "Luxury Problems"
        // Two tracks of the same canonical album: one with year populated, one without
        val itemWithYear =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails(albumName, artist, year = 2012)
                    title = "Numb"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val itemWithoutYear =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails(albumName, artist, year = null)
                    title = "Lost and Found"
                    trackNumber = 2
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
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

    "DefaultAlbumRegistry orderedValues returns buckets in album name then artist then year order" {
        val artistA = Artist.of("Autechre")
        val artistB = Artist.of("Bjork", CountryCode.IS)
        val artistC = Artist.of("Can")

        val itemConfield =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artistA
                    this.album = AlbumDetails("Confield", artistA, year = 2001)
                    title = "VI Scose Poise"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val itemHomogenic =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artistB
                    this.album = AlbumDetails("Homogenic", artistB, year = 1997)
                    title = "Joga"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val itemTago =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artistC
                    this.album = AlbumDetails("Tago Mago", artistC, year = 1971)
                    title = "Paperhouse"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

        // All items are seeded before the registry is created so the projection initializes
        // from the fully-populated repository — the ordered TreeMap comparator reads only
        // already-cached values during the seed, avoiding a lirp edge case in incremental inserts.
        // Add in reverse order to confirm projection ordering is not insertion-order.
        registry.close()
        repository.add(itemTago)
        repository.add(itemHomogenic)
        repository.add(itemConfield)
        registry = DefaultAlbumRegistry(repository)
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe 3
            registry.orderedValues().map { it.album.name } shouldBe listOf("Confield", "Homogenic", "Tago Mago")
        }
    }

    "DefaultAlbumRegistry orderedValues places blank name bucket last" {
        val artist = Artist.of("Unknown Artist")

        val itemNamed =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = AlbumDetails("Arca", artist)
                    title = "Piel"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val itemBlank =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = Artist.UNKNOWN
                    this.album = AlbumDetails("", Artist.UNKNOWN)
                    title = "Untitled"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

        // Seed repo before creating the registry so all buckets are built from stable
        // initial state rather than incremental events.
        registry.close()
        repository.add(itemBlank)
        repository.add(itemNamed)
        registry = DefaultAlbumRegistry(repository)
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe 2
            val names = registry.orderedValues().map { it.album.name }
            names.first() shouldBe "Arca"
            names.last() shouldBe ""
        }
    }

    "DefaultAlbumRegistry orderedValues preserves correct position for both buckets" {
        val artistA = Artist.of("Actress", CountryCode.UK)
        val artistP = Artist.of("Portishead", CountryCode.UK)

        val itemActress =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artistA
                    this.album = AlbumDetails("Splazsh", artistA, year = 2010)
                    title = "Hubble"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val itemPortishead =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artistP
                    this.album = AlbumDetails("Dummy", artistP, year = 1994)
                    title = "Sour Times"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

        // Seed both items before registry creation so the ordered projection builds from stable
        // initial state — "Dummy" < "Splazsh" alphabetically, so Dummy must come first.
        registry.close()
        repository.add(itemActress)
        repository.add(itemPortishead)
        registry = DefaultAlbumRegistry(repository)
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe 2
            registry.orderedValues().map { it.album.name } shouldBe listOf("Dummy", "Splazsh")
        }
    }

    "DefaultAlbumRegistry orderedValues bucket preserves disc then track order within album" {
        val artist = Artist.of("Pink Floyd")
        val album = AlbumDetails("The Wall", artist, year = 1979)

        val track1Disc1 =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "In the Flesh?"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        val track1Disc2 =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Hey You"
                    trackNumber = 1
                    discNumber = 2
                }.next(),
                files.metadataIO
            )
        val track2Disc1 =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "The Thin Ice"
                    trackNumber = 2
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

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