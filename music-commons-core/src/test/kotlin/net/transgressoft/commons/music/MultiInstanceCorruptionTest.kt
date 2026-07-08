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

package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.TwoCoreMusicLibraries
import net.transgressoft.commons.music.testing.buildTwoCoreLibrariesWithoutClosing
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi

/*
 * Reproduces the multi-instance registry-overwrite corruption on the headless Core facade and
 * documents the cross-library entity-identity contract the fix must preserve.
 *
 * The corruption case deliberately does NOT install RegistryIsolationExtension and does NOT bracket
 * with deregister/register: it must exercise the unisolated path where constructing a second
 * CoreMusicLibrary overwrites the audio-item registry slot the first library still relies on for
 * playlist aggregate resolution. Because item ids are repository-local (both repositories count from
 * 1), the first library's playlist reference resolves against the second library's repository and
 * silently returns a DIFFERENT item that happens to share the id — no exception is thrown. The test
 * asserts the healthy invariant (the playlist resolves the library's OWN item, compared by the
 * cross-repository-stable uniqueId), which a correct system must satisfy and which is violated today.
 */
@DisplayName("MultiInstanceCorruptionTest")
@ExperimentalCoroutinesApi
internal class MultiInstanceCorruptionTest : StringSpec({

    val files = virtualFiles()

    lateinit var libraries: TwoCoreMusicLibraries

    beforeEach {
        libraries = buildTwoCoreLibrariesWithoutClosing(files.metadataIO)
    }

    afterEach {
        libraries.close()
    }

    "second CoreMusicLibrary construction silently resolves the first library's playlist item from the wrong repository" {
        val audioLibraryA = libraries.libraryA.audioLibrary()
        val audioLibraryB = libraries.libraryB.audioLibrary()
        val playlistHierarchyA = libraries.libraryA.playlistHierarchy()

        // Distinct items in each library. Both repositories assign id 1 (per-instance counters),
        // so library A's playlist reference (id 1) collides with a DIFFERENT item in library B.
        val itemInA = audioLibraryA.createFromFile(files.virtualAudioFile().next())
        audioLibraryB.createFromFile(files.virtualAudioFile().next())

        val playlist = playlistHierarchyA.createPlaylist("Library A Playlist")
        playlist.addAudioItem(itemInA)

        val resolvedItems = playlist.audioItems
        resolvedItems.any { it.uniqueId == itemInA.uniqueId } shouldBe true
    }

    "an audio item created in two libraries shares equality and uniqueId despite repository-local ids" {
        val audioLibraryA = libraries.libraryA.audioLibrary()
        val audioLibraryB = libraries.libraryB.audioLibrary()

        // Decoy in library B so the shared item receives a different repository-local id there.
        audioLibraryB.createFromFile(files.virtualAudioFile().next())

        val sharedPath = files.virtualAudioFile().next()
        val inA = audioLibraryA.createFromFile(sharedPath)
        val inB = audioLibraryB.createFromFile(sharedPath)

        // Ids are repository-assigned and therefore differ across libraries...
        inA.id shouldNotBe inB.id
        // ...but uniqueId (identity-defining fields) and content equality are stable across repositories.
        inA.uniqueId shouldBe inB.uniqueId
        inA shouldBe inB
    }
})