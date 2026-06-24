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
import net.transgressoft.lirp.event.StandardCrudEvent
import net.transgressoft.lirp.persistence.VolatileRepository
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.seconds

internal class DefaultGenreCatalogRegistryTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var registry: DefaultGenreCatalogRegistry<AudioItem>

    beforeEach {
        repository = VolatileRepository("DefaultGenreCatalogRegistryTest")
        registry = DefaultGenreCatalogRegistry(repository)
    }

    afterEach {
        registry.close()
    }

    "DefaultGenreCatalogRegistry creates genre catalog when item is added to repository" {
        val artist = Artist.of("Radiohead", CountryCode.UK)
        val album = Album("OK Computer", artist)
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = setOf(Alternative)
                    title = "Karma Police"
                    trackNumber = 4
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

        repository.add(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            registry.size() shouldBe 1
            registry.findById(Alternative) shouldBePresent { catalog ->
                catalog.genre shouldBe Alternative
                catalog.size shouldBe 1
                catalog.audioItems.shouldContainOnly(audioItem)
            }
        }
    }

    "DefaultGenreCatalogRegistry item with two genres appears in both genre buckets simultaneously" {
        // Multi-membership: a single item tagged with both Rock and Electronic must appear
        // in each of those buckets at the same time.
        val artist = Artist.of("Radiohead", CountryCode.UK)
        val album = Album("Kid A", artist)
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = setOf(Rock, Electronic)
                    title = "Everything in Its Right Place"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

        repository.add(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            // two distinct genre buckets created for the same item
            registry.size() shouldBe 2
            registry.findById(Rock) shouldBePresent { catalog ->
                catalog.size shouldBe 1
                catalog.audioItems.shouldContainOnly(audioItem)
            }
            registry.findById(Electronic) shouldBePresent { catalog ->
                catalog.size shouldBe 1
                catalog.audioItems.shouldContainOnly(audioItem)
            }
        }
    }

    "DefaultGenreCatalogRegistry item with empty genres set appears in no genre bucket" {
        val artist = Artist.of("Aphex Twin")
        val album = Album("Selected Ambient Works", artist)
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = emptySet()
                    title = "Xtal"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

        repository.add(audioItem)
        reactive.advance()

        // No genre bucket should exist for an item with no genres — advance() drains the
        // test scheduler to idle, so no async work can create a bucket after this point.
        registry.isEmpty shouldBe true
        registry.size() shouldBe 0
    }

    "DefaultGenreCatalogRegistry re-buckets item when genres change via repository UPDATE" {
        val artist = Artist.of("Bjork", CountryCode.IS)
        val album = Album("Homogenic", artist)
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = setOf(Electronic)
                    title = "Joga"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findById(Electronic).isPresent shouldBe true }

        (audioItem as MutableAudioItem).genres = setOf(Alternative)
        repository.emitAsync(StandardCrudEvent.Update(audioItem, audioItem))
        reactive.advance()

        eventually(2.seconds) {
            registry.findById(Alternative).isPresent shouldBe true
            registry.findById(Electronic).shouldBeEmpty()
        }
    }

    "DefaultGenreCatalogRegistry removes genre catalog when last item is removed from repository" {
        val artist = Artist.of("Burial", CountryCode.UK)
        val album = Album("Untrue", artist)
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = setOf(Electronic)
                    title = "Archangel"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findById(Electronic).isPresent shouldBe true }

        repository.remove(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            registry.isEmpty shouldBe true
            registry.findById(Electronic).shouldBeEmpty()
        }
    }

    "DefaultGenreCatalogRegistry removing multi-genre item removes it from all its genre buckets" {
        val artist = Artist.of("Beck")
        val album = Album("Odelay", artist)
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = setOf(Alternative, Folk, Rock)
                    title = "Where Its At"
                    trackNumber = 5
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.size() shouldBe 3 }

        repository.remove(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            registry.isEmpty shouldBe true
            registry.findById(Alternative).shouldBeEmpty()
            registry.findById(Folk).shouldBeEmpty()
            registry.findById(Rock).shouldBeEmpty()
        }
    }

    "DefaultGenreCatalogRegistry emits CREATE event when first item for genre is added" {
        val receivedEvents = mutableListOf<CrudEvent<Genre, GenreCatalog<AudioItem>>>()
        registry.genreCatalogPublisher.subscribe(CREATE) { receivedEvents.add(it) }

        val artist = Artist.of("Miles Davis")
        val album = Album("Kind of Blue", artist)
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = setOf(Jazz)
                    title = "So What"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )

        repository.add(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            receivedEvents.size shouldBe 1
            receivedEvents[0].entities.values.first().genre shouldBe Jazz
        }
    }

    "DefaultGenreCatalogRegistry emits DELETE event when last item of genre is removed" {
        val artist = Artist.of("Coltrane")
        val album = Album("Giant Steps", artist)
        val audioItem =
            createAudioItem(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = setOf(Jazz)
                    title = "Giant Steps"
                    trackNumber = 1
                    discNumber = 1
                }.next(),
                files.metadataIO
            )
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findById(Jazz).isPresent shouldBe true }

        val deleteEvents = mutableListOf<CrudEvent<Genre, GenreCatalog<AudioItem>>>()
        registry.genreCatalogPublisher.subscribe(DELETE) { deleteEvents.add(it) }

        repository.remove(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            deleteEvents.size shouldBe 1
            deleteEvents[0].entities.values.first().genre shouldBe Jazz
        }
    }

    "DefaultGenreCatalogRegistry builds catalog from items already in repository at construction" {
        val artist = Artist.of("Pixies")
        val album = Album("Doolittle", artist)
        val items =
            files.virtualAlbumAudioFiles(artist, album, size = 3..4).next()
                .mapIndexed { idx, path -> createAudioItem(path, idx + 1, files.metadataIO) }

        items.forEach {
            (it as MutableAudioItem).genres = setOf(Alternative)
            repository.add(it)
        }
        registry.close()
        registry = DefaultGenreCatalogRegistry(repository)
        reactive.advance()

        eventually(2.seconds) {
            registry.findById(Alternative) shouldBePresent { catalog ->
                catalog.size shouldBe items.size
                catalog.audioItems.shouldContainOnly(items)
            }
        }
    }
})