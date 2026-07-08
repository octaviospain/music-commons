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

package net.transgressoft.commons.fx.music

import net.transgressoft.commons.music.audio.VolatileAudioMetadataIO

/**
 * Holds two [FXMusicLibrary] instances constructed sequentially without closing the first,
 * reproducing the registry-slot eviction crux on the JavaFX facade: both libraries bind their
 * audio-item repository to the same process-wide registry, so the second construction silently
 * overwrites the slot the first still relies on for playlist aggregate resolution.
 *
 * [close] releases the libraries in reverse construction order — [libraryB] (the current slot
 * owner) first, then [libraryA] — each guarded so a failing first close still releases the second.
 */
class TwoFXMusicLibraries(
    val libraryA: FXMusicLibrary,
    val libraryB: FXMusicLibrary
) : AutoCloseable {

    override fun close() {
        runCatching { libraryB.close() }
        runCatching { libraryA.close() }
    }
}

/**
 * Builds two [FXMusicLibrary] instances sharing [metadataIO], with NO [FXMusicLibrary.close] between
 * them, so [TwoFXMusicLibraries.libraryB] evicts the registry slot [TwoFXMusicLibraries.libraryA]
 * was using. Both libraries stay live on return.
 *
 * The caller MUST have initialized the JavaFX toolkit (e.g. `FxToolkit.registerPrimaryStage()`)
 * before invoking this factory — the fixture deliberately does not start the toolkit itself, so
 * headless test suites keep full control of toolkit lifecycle.
 */
fun buildTwoFXLibrariesWithoutClosing(
    metadataIO: VolatileAudioMetadataIO = VolatileAudioMetadataIO()
): TwoFXMusicLibraries {
    val libraryA = FXMusicLibrary.builder().metadataIO(metadataIO).build()
    val libraryB = FXMusicLibrary.builder().metadataIO(metadataIO).build()
    return TwoFXMusicLibraries(libraryA, libraryB)
}