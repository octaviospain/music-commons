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
import net.transgressoft.commons.music.testing.registryIsolation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next

/*
 * Asserts the fail-fast guard on the Core facade and documents the cross-library entity-identity
 * contract.
 *
 * The fail-fast case exercises the unisolated path where constructing a second CoreMusicLibrary
 * while one is live throws IllegalStateException — the guard rejects the second construction
 * before any registry slot is overwritten, leaving the first library's state intact.
 *
 * The identity case documents that audio items created from the same path in independently-managed
 * libraries share uniqueId and content equality even though repository-local ids differ.
 */
@DisplayName("MultiInstanceCorruptionTest")
internal class MultiInstanceCorruptionTest : StringSpec({

    registryIsolation()
    val files = virtualFiles()

    lateinit var libraries: TwoCoreMusicLibraries

    beforeEach {
        libraries = buildTwoCoreLibrariesWithoutClosing(files.metadataIO)
    }

    afterEach {
        libraries.close()
    }

    "second CoreMusicLibrary construction throws IllegalStateException while the first is still live" {
        shouldThrow<IllegalStateException> {
            libraries.attemptSecondConstruction()
        }
    }

    "an audio item created in two independently-managed libraries shares equality and uniqueId despite repository-local ids" {
        val sharedPath = files.virtualAudioFile().next()

        val inA = libraries.libraryA.audioLibrary().createFromFile(sharedPath)

        // Build a second library only after the first is closed so the slot is free.
        libraries.close()
        val libraryB = CoreMusicLibrary.builder().metadataIO(files.metadataIO).build()
        try {
            // Decoy in library B so the shared item receives a different repository-local id there.
            libraryB.audioLibrary().createFromFile(files.virtualAudioFile().next())
            val inB = libraryB.audioLibrary().createFromFile(sharedPath)

            // Ids are repository-assigned and therefore differ across libraries...
            inA.id shouldNotBe inB.id
            // ...but uniqueId (identity-defining fields) and content equality are stable across repositories.
            inA.uniqueId shouldBe inB.uniqueId
            inA shouldBe inB
        } finally {
            libraryB.close()
        }
    }
})