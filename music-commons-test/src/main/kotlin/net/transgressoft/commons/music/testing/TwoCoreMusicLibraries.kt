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
 * Holds two [CoreMusicLibrary] instances that were constructed sequentially without closing the
 * first, reproducing the registry-slot eviction crux: both libraries bind their audio-item
 * repository to the same process-wide registry, so the second construction silently overwrites the
 * slot the first still relies on for playlist aggregate resolution.
 *
 * [close] releases the libraries in reverse construction order — [libraryB] (the current slot
 * owner) first, then [libraryA] — each guarded so a failing first close still releases the second.
 */
class TwoCoreMusicLibraries(
    val libraryA: CoreMusicLibrary,
    val libraryB: CoreMusicLibrary
) : AutoCloseable {

    override fun close() {
        runCatching { libraryB.close() }
        runCatching { libraryA.close() }
    }
}

/**
 * Builds two [CoreMusicLibrary] instances sharing [metadataIO], with NO [CoreMusicLibrary.close]
 * between them, so [TwoCoreMusicLibraries.libraryB] evicts the registry slot
 * [TwoCoreMusicLibraries.libraryA] was using. Both libraries stay live on return, letting a test
 * observe the resulting cross-instance resolution behaviour.
 */
fun buildTwoCoreLibrariesWithoutClosing(
    metadataIO: VolatileAudioMetadataIO = VolatileAudioMetadataIO()
): TwoCoreMusicLibraries {
    val libraryA = CoreMusicLibrary.builder().metadataIO(metadataIO).build()
    val libraryB = CoreMusicLibrary.builder().metadataIO(metadataIO).build()
    return TwoCoreMusicLibraries(libraryA, libraryB)
}