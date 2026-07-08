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
 * Holds a live [FXMusicLibrary] and a deferred second-construction attempt, enabling tests to
 * assert the fail-fast single-live-instance contract on the JavaFX facade.
 *
 * [libraryA] is the live library. [attemptSecondConstruction] is a lambda whose invocation
 * triggers a second `FXMusicLibrary.builder().build()` call while [libraryA] is still live;
 * callers wrap it in `shouldThrow<IllegalStateException>` to assert the guarded rejection.
 *
 * [close] releases [libraryA]. Because the second construction is intentionally rejected, no
 * second library is ever live and no additional cleanup is required.
 */
class TwoFXMusicLibraries(
    val libraryA: FXMusicLibrary,
    val attemptSecondConstruction: () -> FXMusicLibrary
) : AutoCloseable {

    override fun close() {
        runCatching { libraryA.close() }
    }
}

/**
 * Builds a live [FXMusicLibrary] and returns it together with a deferred lambda that constructs
 * a second library while the first is still live, using [metadataIO] for both.
 *
 * The caller MUST have initialized the JavaFX toolkit (e.g. `FxToolkit.registerPrimaryStage()`)
 * before invoking this factory — the fixture deliberately does not start the toolkit itself, so
 * headless test suites keep full control of toolkit lifecycle.
 *
 * The returned [TwoFXMusicLibraries.attemptSecondConstruction] will throw [IllegalStateException]
 * when called, because the fail-fast guard rejects a second construction while [TwoFXMusicLibraries.libraryA]
 * is live. Callers should wrap the invocation in `shouldThrow<IllegalStateException>`.
 */
fun buildTwoFXLibrariesWithoutClosing(
    metadataIO: VolatileAudioMetadataIO = VolatileAudioMetadataIO()
): TwoFXMusicLibraries {
    val libraryA = FXMusicLibrary.builder().metadataIO(metadataIO).build()
    return TwoFXMusicLibraries(libraryA) {
        FXMusicLibrary.builder().metadataIO(metadataIO).build()
    }
}