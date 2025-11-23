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

import net.transgressoft.commons.event.MutationEvent
import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAlbumAudioFiles
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

typealias ArtistCatalogMutation = MutationEvent<Artist, ArtistCatalog<AudioItem>>

@ExperimentalCoroutinesApi
class MutableArtistCatalogTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
        unmockkAll()
    }

    "MUTATE event is published when audio item is added to empty catalog" {
        val expectedArtist = Arb.artist().next()
        val expectedAlbum = Arb.album().next()
        val audioItem =
            Arb.audioItem {
                artist = expectedArtist
                album = expectedAlbum
                trackNumber = 1
            }.next()

        val catalog = MutableArtistCatalog<AudioItem>(expectedArtist)
        val receivedEvents = mutableListOf<ArtistCatalogMutation>()
        catalog.subscribe(receivedEvents::add)

        catalog.addAudioItem(audioItem) shouldBe true

        catalog.albums should {
            it.size shouldBe 1
            it.first().albumName shouldBe expectedAlbum.name
            it.first().shouldContainOnly(audioItem)
        }

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        receivedEvents[0].shouldBeInstanceOf<ArtistCatalogMutation>()
        receivedEvents[0].type shouldBe MutationEvent.Type.MUTATE
        receivedEvents[0].newEntity should { catalogSnapshot ->
            catalogSnapshot.artist shouldBe expectedArtist
            catalogSnapshot.size shouldBe 1
            catalogSnapshot.albums.size shouldBe 1
            catalogSnapshot.albumAudioItems(expectedAlbum.name).shouldContainOnly(audioItem)
        }
        receivedEvents[0].oldEntity should { catalogSnapshot ->
            catalogSnapshot.artist shouldBe expectedArtist
            catalogSnapshot.size shouldBe 0
            catalogSnapshot.albums.size shouldBe 0
            catalogSnapshot.isEmpty shouldBe true
        }
    }

    "MUTATE event is published when second audio item is added to same album" {
        val expectedArtist = Arb.artist().next()
        val expectedAlbum = Arb.album().next()
        val firstAudioItem =
            Arb.audioItem {
                artist = expectedArtist
                album = expectedAlbum
                trackNumber = 1
            }.next()

        val catalog = MutableArtistCatalog(firstAudioItem) // event is published on constructor with audioItem, but we are not subscribed yet
        val receivedEvents = mutableListOf<ArtistCatalogMutation>()
        catalog.subscribe(receivedEvents::add)

        val secondAudioItem =
            Arb.audioItem {
                artist = expectedArtist
                album = expectedAlbum
                trackNumber = 2
            }.next()

        catalog.addAudioItem(secondAudioItem) shouldBe true

        catalog.albums should {
            it.size shouldBe 1
            it.first().albumName shouldBe expectedAlbum.name
            it.first().shouldContainOnly(firstAudioItem, secondAudioItem)
        }

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        receivedEvents[0].newEntity should { catalogSnapshot ->
            catalogSnapshot.size shouldBe 2
            catalogSnapshot.albums.size shouldBe 1
            catalogSnapshot.albumAudioItems(expectedAlbum.name).shouldContainOnly(firstAudioItem, secondAudioItem)
        }
        receivedEvents[0].oldEntity should { catalogSnapshot ->
            catalogSnapshot.size shouldBe 1
            catalogSnapshot.albums.size shouldBe 1
            catalogSnapshot.albumAudioItems(expectedAlbum.name).shouldContainOnly(firstAudioItem)
        }
    }

    "MUTATE event is published when audio item is added to different album" {
        val expectedArtist = Arb.artist().next()
        val album1 = Arb.album().next()
        val audioItem1 =
            Arb.audioItem {
                artist = expectedArtist
                album = album1
            }.next()

        val catalog = MutableArtistCatalog(audioItem1)
        val receivedEvents = mutableListOf<ArtistCatalogMutation>()
        catalog.subscribe(receivedEvents::add)

        val album2 = Arb.album().next()

        val audioItem2 =
            Arb.audioItem {
                artist = expectedArtist
                album = album2
            }.next()

        catalog.addAudioItem(audioItem2) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        receivedEvents[0].newEntity should { catalogSnapshot ->
            catalogSnapshot.albums.size shouldBe 2
            catalogSnapshot.size shouldBe 2
        }
        receivedEvents[0].oldEntity should { catalogSnapshot ->
            catalogSnapshot.albums.size shouldBe 1
            catalogSnapshot.size shouldBe 1
        }
    }

    "MUTATE event is published when album audio items ordering changed due to a audio item track number changed" {
        val albumAudioItems = Arb.virtualAlbumAudioFiles().next().map(::MutableAudioItem)

        val catalog = MutableArtistCatalog(albumAudioItems)
        val receivedEvents = mutableListOf<ArtistCatalogMutation>()

        catalog.subscribe(receivedEvents::add)

        catalog.albums.first().shouldBeOrdered()

        val audioItem = albumAudioItems.first()

        // change the trackNumber to the highest on the album should make it be reordered to the last in the list
        val newTrackNumber = albumAudioItems.last().trackNumber?.plus(1)?.toShort()
        audioItem.trackNumber = newTrackNumber
        catalog.mergeAudioItem(audioItem)

        catalog.albums.first().shouldBeOrdered()

        val album = albumAudioItems.first().album.name

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        receivedEvents[0].newEntity should { catalogSnapshot ->
            catalogSnapshot.size shouldBe albumAudioItems.size
            catalogSnapshot.albumAudioItems(album).shouldContainOnly(albumAudioItems)
            // After reordering, the item should be at the last position (highest track number)
            catalogSnapshot.albumAudioItems(album).last().trackNumber shouldBe newTrackNumber
        }
        receivedEvents[0].oldEntity should { catalogSnapshot ->
            // In the old snapshot, the item was at the first position
            // But since items are mutable references, both snapshots see the new mutated track number,
            // So we check the first item (which was first before reordering) has the mutated trackNumber
            catalogSnapshot.albumAudioItems(album).first().trackNumber shouldBe newTrackNumber
        }
    }

    "MUTATE event is not published when audio item track number is changed via merge and is the only one in the catalog" {
        val audioItem = MutableAudioItem(Arb.virtualAudioFile().next())

        val catalog = MutableArtistCatalog(audioItem)
        val receivedEvents = mutableListOf<ArtistCatalogMutation>()

        catalog.subscribe(receivedEvents::add)

        audioItem.trackNumber = audioItem.trackNumber?.plus(1)?.toShort()
        catalog.mergeAudioItem(audioItem) shouldBe false

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.isEmpty() shouldBe true
    }

    "MUTATE event is published when album audio items ordering changed due to an audio item disc number changed" {
        val albumAudioItems = Arb.virtualAlbumAudioFiles().next().map(::MutableAudioItem)

        val catalog = MutableArtistCatalog(albumAudioItems)
        val receivedEvents = mutableListOf<ArtistCatalogMutation>()

        catalog.subscribe(receivedEvents::add)

        catalog.albums.first().shouldBeOrdered()

        val audioItem = albumAudioItems.first()
        val originalDiscNumber = audioItem.discNumber

        // change the discNumber to the next one, should make it be reordered to the last in the list
        audioItem.discNumber = albumAudioItems.last().discNumber?.plus(1)?.toShort()
        catalog.mergeAudioItem(audioItem) shouldBe true

        catalog.albums.first().shouldBeOrdered()

        val album = albumAudioItems.first().album.name

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        receivedEvents[0].newEntity should { catalogSnapshot ->
            catalogSnapshot.size shouldBe albumAudioItems.size
            catalogSnapshot.albumAudioItems(album).shouldContainOnly(albumAudioItems)
            // After reordering, the item should be at the last position (highest disc number)
            catalogSnapshot.albumAudioItems(album).last().discNumber shouldBe originalDiscNumber?.plus(1)?.toShort()
        }
        receivedEvents[0].oldEntity should { catalogSnapshot ->
            // In the old snapshot, the item was at the first position
            // But since items are mutable references, both snapshots see the new mutated disc number,
            // So we check the first item (which was first before reordering) has the mutated discNumber
            catalogSnapshot.albumAudioItems(album).first().discNumber shouldBe originalDiscNumber?.plus(1)?.toShort()
        }
    }

    "MUTATE event is not published when audio item disc number is changed via merge and is the only one in the catalog" {
        val audioItem = MutableAudioItem(Arb.virtualAudioFile().next())

        val catalog = MutableArtistCatalog(audioItem)
        val receivedEvents = mutableListOf<ArtistCatalogMutation>()

        catalog.subscribe(receivedEvents::add)

        audioItem.discNumber = audioItem.discNumber?.plus(1)?.toShort()
        catalog.mergeAudioItem(audioItem) shouldBe false

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.isEmpty() shouldBe true
    }

    "MUTATE event is published when audio item is removed from catalog with multiple items" {
        val albumAudioItems = Arb.virtualAlbumAudioFiles().next().map(::MutableAudioItem)

        val catalog = MutableArtistCatalog(albumAudioItems)
        val receivedEvents = mutableListOf<ArtistCatalogMutation>()

        catalog.subscribe(receivedEvents::add)

        val originalSize = albumAudioItems.size
        val firstAudioItem = albumAudioItems.first()
        catalog.removeAudioItem(firstAudioItem) shouldBe true

        catalog.containsAudioItem(firstAudioItem) shouldBe false

        testDispatcher.scheduler.advanceUntilIdle()

        val album = firstAudioItem.album.name
        receivedEvents.size shouldBe 1
        receivedEvents[0].newEntity should { catalogSnapshot ->
            catalogSnapshot.size shouldBe originalSize - 1
            catalogSnapshot.albumAudioItems(album).shouldNotContain(firstAudioItem)
        }
        receivedEvents[0].oldEntity should { catalogSnapshot ->
            catalogSnapshot.size shouldBe originalSize
            catalogSnapshot.albumAudioItems(album).shouldContain(firstAudioItem)
        }
    }

    "MUTATE event is published when last audio item is removed from catalog" {
        val audioItem = Arb.audioItem().next()

        val catalog = MutableArtistCatalog(audioItem)
        val receivedEvents = mutableListOf<ArtistCatalogMutation>()

        catalog.subscribe(receivedEvents::add)

        catalog.removeAudioItem(audioItem) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        receivedEvents.size shouldBe 1
        receivedEvents[0].newEntity should { catalogSnapshot ->
            catalogSnapshot.isEmpty shouldBe true
            catalogSnapshot.albums.size shouldBe 0
        }
        receivedEvents[0].oldEntity should { catalogSnapshot ->
            catalogSnapshot.size shouldBe 1
            catalogSnapshot.albums.size shouldBe 1
        }
    }

    "MUTATE event is published when removing audio item from one album leaves another album" {
        val album1 = Arb.virtualAlbumAudioFiles().next().map(::MutableAudioItem)
        val album2 = Arb.virtualAlbumAudioFiles().next().map(::MutableAudioItem)
        val catalog = MutableArtistCatalog(album1 + album2)

        val receivedEvents = mutableListOf<ArtistCatalogMutation>()

        catalog.subscribe(receivedEvents::add)

        val originalSize = album1.size + album2.size
        val audioItem = album1.first()
        catalog.removeAudioItem(audioItem) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        val album = audioItem.album.name
        receivedEvents.size shouldBe 1
        receivedEvents[0].newEntity should { catalogSnapshot ->
            catalogSnapshot.size shouldBe originalSize - 1
            catalogSnapshot.albumAudioItems(album).shouldNotContain(audioItem)
        }
        receivedEvents[0].oldEntity should { catalogSnapshot ->
            catalogSnapshot.size shouldBe originalSize
            catalogSnapshot.albumAudioItems(album).shouldContain(audioItem)
        }
    }

    "multiple subscribers should all receive MUTATE events" {
        val expectedArtist = Arb.artist().next()
        val catalog = MutableArtistCatalog<AudioItem>(expectedArtist)

        val subscriber1Events = mutableListOf<ArtistCatalogMutation>()
        val subscriber2Events = mutableListOf<ArtistCatalogMutation>()
        val subscriber3Events = mutableListOf<ArtistCatalogMutation>()

        catalog.subscribe(subscriber1Events::add)
        catalog.subscribe(subscriber2Events::add)
        catalog.subscribe(subscriber3Events::add)

        val expectedAlbum = Arb.album().next()

        val audioItem =
            Arb.audioItem {
                artist = expectedArtist
                album = expectedAlbum
            }.next()

        catalog.addAudioItem(audioItem) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        subscriber1Events.size shouldBe 1
        subscriber2Events.size shouldBe 1
        subscriber3Events.size shouldBe 1
    }
})