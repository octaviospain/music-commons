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
})