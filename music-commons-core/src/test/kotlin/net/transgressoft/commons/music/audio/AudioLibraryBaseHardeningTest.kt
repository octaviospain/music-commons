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

package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.playlist.DefaultPlaylistHierarchy
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.testing.registryIsolation
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.lang.ref.SoftReference
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Regression tests pinning the Wave-1 fixes to [AudioLibraryBase] and its collaborators:
 * bounded [AudioLibraryBase.newId] (#16), noCover reset after SoftReference eviction (#17),
 * foreign-origin metadata re-wire (#7), UNASSIGNED_ID rejection in createPlaylist (#14),
 * and the unsubscribed-hierarchy first-use guard (#5).
 */
@DisplayName("AudioLibraryBase hardening — regression tests for Wave-1 fixes")
@ExperimentalCoroutinesApi
internal class AudioLibraryBaseHardeningTest : StringSpec({

    registryIsolation()
    val reactive = reactiveScope()
    val files = virtualFiles()

    "#16 newId() throws IllegalStateException when the id space is saturated" {
        val saturated =
            object : VolatileRepository<Int, AudioItem>("SaturatedRepository") {
                override fun contains(id: Int): Boolean = true
            }
        val library = TestAudioLibrary(saturated, files.metadataIO)
        try {
            val audioFile = files.virtualAudioFile().next()
            val ex = shouldThrow<IllegalStateException> { library.createFromFile(audioFile) }
            ex.message.shouldNotBeNull()
            ex.message!!.contains("exhausted") shouldBe true
        } finally {
            library.close()
        }
    }

    "#17 album coverImageBytes reloads after SoftReference eviction (noCover is not permanent)" {
        val albumDetails = Arb.album().next()
        val expectedCover = byteArrayOf(1, 2, 3, 4, 5)
        val track =
            Arb.audioItem {
                this.album = albumDetails
                coverImageBytes = expectedCover
            }.next()
        val album = ImmutableAlbum(albumDetails, listOf(track))

        // First access loads and caches the cover
        album.coverImageBytes shouldBe expectedCover

        // Simulate GC eviction: replace the live SoftReference with one whose referent is null
        val coverRefField = ImmutableAlbum::class.java.getDeclaredField("coverRef")
        coverRefField.isAccessible = true
        coverRefField.set(album, SoftReference(null as ByteArray?))

        // After simulated eviction, coverImageBytes must reload (not return null permanently)
        album.coverImageBytes shouldBe expectedCover
    }

    "#7 adding a foreign-origin item re-wires its metadataIO to the target library" {
        val sourceMetadataIO = files.metadataIO
        val targetMetadataIO = JAudioTaggerMetadataIO()

        // Phase 1: create an item in library1 so its metadataIO is sourceMetadataIO
        val audioFile = files.virtualAudioFile().next()
        val item =
            DefaultAudioLibrary(VolatileRepository("Hardening-L1"), sourceMetadataIO).use { library1 ->
                library1.createFromFile(audioFile) as MutableAudioItem
            }
        item.metadataIO shouldBe sourceMetadataIO

        // Phase 2: add the foreign item to library2 (different metadataIO) — it must be re-wired
        DefaultAudioLibrary(VolatileRepository("Hardening-L2"), targetMetadataIO).use { library2 ->
            library2.add(item)
            reactive.advance()
            item.metadataIO shouldBe targetMetadataIO
        }
    }

    "#14 createPlaylist with UNASSIGNED_ID in the item list throws IllegalArgumentException" {
        val library =
            CoreMusicLibrary.builder()
                .audioRepository(VolatileRepository("Hardening-UNASSIGNED"))
                .metadataIO(files.metadataIO)
                .build()
        try {
            shouldThrow<IllegalArgumentException> {
                library.createPlaylist("BadPlaylist", listOf(UNASSIGNED_ID))
            }
        } finally {
            library.close()
        }
    }

    "#5 standalone hierarchy fails fast on first mutating call (must be wired via builder)" {
        val hierarchy = DefaultPlaylistHierarchy()
        try {
            shouldThrow<IllegalStateException> {
                hierarchy.createPlaylist("StandalonePlaylist")
            }
        } finally {
            hierarchy.close()
        }
    }

    "#5 builder-wired hierarchy removes audio items from playlists when the item is deleted" {
        val library =
            CoreMusicLibrary.builder()
                .audioRepository(VolatileRepository("Hardening-Delete"))
                .metadataIO(files.metadataIO)
                .build()
        try {
            val audioFile = files.virtualAudioFile().next()
            val item = library.audioItemFromFile(audioFile)
            reactive.advance()

            val playlist = library.createPlaylist("TestPlaylist", listOf(item))
            reactive.advance()
            playlist.audioItems.toList() shouldNotBe emptyList<Any>()

            library.audioLibrary().remove(item)
            reactive.advance()

            playlist.audioItems.toList().shouldBeEmpty()
        } finally {
            library.close()
        }
    }
})