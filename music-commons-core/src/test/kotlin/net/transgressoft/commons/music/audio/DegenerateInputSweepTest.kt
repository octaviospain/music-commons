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
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.music.testing.registryIsolation
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Documents the expected outcome for every degenerate or boundary input to the audio library and
 * playlist hierarchy: each row ends with either a clean return value or a documented exception.
 * None of these inputs should cause silent corruption or an infinite loop.
 */
@DisplayName("Degenerate/empty-input sweep — each input produces a documented outcome")
@ExperimentalCoroutinesApi
internal class DegenerateInputSweepTest : StringSpec({

    registryIsolation()
    val reactive = reactiveScope()
    val files = virtualFiles()

    "Empty library returns empty collections for all query methods" {
        val library = TestAudioLibrary(VolatileRepository("DegenerateInputSweepTest-empty"), files.metadataIO)
        try {
            library.size() shouldBe 0
            library.isEmpty shouldBe true
            library.search { true }.shouldBeEmpty()
            library.containsAudioItemWithArtist("any-artist") shouldBe false
            library.containsAudioItemWithAlbum("any-album") shouldBe false
            library.containsAudioItemWithGenre("any-genre") shouldBe false
        } finally {
            library.close()
        }
    }

    "Empty playlist is created and queried without error and returns an empty item list" {
        val hierarchy = net.transgressoft.commons.music.playlist.TestPlaylistHierarchy()
        try {
            val playlist = hierarchy.createPlaylist("EmptyPlaylist")
            playlist.name shouldBe "EmptyPlaylist"
            playlist.audioItems.toList().shouldBeEmpty()
            hierarchy.findByName("EmptyPlaylist").isPresent shouldBe true
        } finally {
            hierarchy.close()
        }
    }

    "Duplicate file path: adding the same physical file twice returns the existing item (idempotent add)" {
        val library =
            CoreMusicLibrary.builder()
                .audioRepository(VolatileRepository("DegenerateInputSweepTest-duplicate"))
                .metadataIO(files.metadataIO)
                .build()
        try {
            val audioFile = files.virtualAudioFile().next()
            val first = library.audioItemFromFile(audioFile)
            reactive.advance()

            val second = library.audioItemFromFile(audioFile)
            reactive.advance()

            // Physical identity: same file → same uniqueId → idempotent add
            second.id shouldBe first.id
            second.uniqueId shouldBe first.uniqueId
            library.audioLibrary().size() shouldBe 1
        } finally {
            library.close()
        }
    }

    "No-genre item is added and projected without error; genre index handles absent genre" {
        val library = TestAudioLibrary(VolatileRepository("DegenerateInputSweepTest-noGenre"), files.metadataIO)
        try {
            val audioFile =
                files.virtualAudioFile {
                    genres = emptySet()
                }.next()

            val item = library.createFromFile(audioFile)
            reactive.advance()

            item.genres.shouldBeEmpty()
            library.size() shouldBe 1
            // Genre index must handle the item without a genre — no-genre items are queryable
            library.containsAudioItemWithGenre("") shouldBe true
        } finally {
            library.close()
        }
    }

    "Int.MAX_VALUE ID-space boundary: exhausted id space causes newId() to throw IllegalStateException" {
        // A repository whose contains(id) always returns true simulates a saturated id space.
        // AudioLibraryBase.newId() retries up to MAX_ID_ALLOCATION_ATTEMPTS and then throws.
        val saturated =
            object : VolatileRepository<Int, AudioItem>("SaturatedRepository") {
                override fun contains(id: Int): Boolean = true
            }
        val library = TestAudioLibrary(saturated, files.metadataIO)
        try {
            val audioFile = files.virtualAudioFile().next()
            val ex = shouldThrow<IllegalStateException> { library.createFromFile(audioFile) }
            ex.message shouldNotBe null
            ex.message!!.contains("exhausted") shouldBe true
        } finally {
            library.close()
        }
    }
})