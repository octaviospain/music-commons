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

package net.transgressoft.commons.music.testing

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.VolatileAudioMetadataIO

/**
 * Holds a single live [CoreMusicLibrary] alongside a deferred second-construction action,
 * enabling tests to assert the fail-fast guard that rejects a concurrent live instance.
 *
 * [libraryA] is built eagerly and remains live. [attemptSecondConstruction] is a lambda that,
 * when invoked, attempts to build a second `CoreMusicLibrary` while [libraryA] is still alive —
 * this is expected to throw [IllegalStateException] because the registry slot is already occupied.
 *
 * [close] releases [libraryA] (the only live library this holder owns).
 */
class TwoCoreMusicLibraries(
    val libraryA: CoreMusicLibrary,
    val attemptSecondConstruction: () -> CoreMusicLibrary
) : AutoCloseable {

    override fun close() {
        runCatching { libraryA.close() }
    }
}

/**
 * Builds a [CoreMusicLibrary] as [TwoCoreMusicLibraries.libraryA] and captures the second-build
 * attempt as a deferred lambda, sharing [metadataIO] across both.
 *
 * The second construction is deferred so tests can assert it throws [IllegalStateException] via
 * `shouldThrow<IllegalStateException> { libraries.attemptSecondConstruction() }` — if it were
 * eager, the factory itself would throw before returning the fixture.
 */
fun buildTwoCoreLibrariesWithoutClosing(
    metadataIO: VolatileAudioMetadataIO = VolatileAudioMetadataIO()
): TwoCoreMusicLibraries {
    val libraryA = CoreMusicLibrary.builder().metadataIO(metadataIO).build()
    return TwoCoreMusicLibraries(
        libraryA = libraryA,
        attemptSecondConstruction = { CoreMusicLibrary.builder().metadataIO(metadataIO).build() }
    )
}