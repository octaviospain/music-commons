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

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylist
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.lirp.persistence.LirpContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import kotlinx.coroutines.ExperimentalCoroutinesApi

/*
 * Asserts the fail-fast single-live-instance contract on the JavaFX facade: constructing a
 * second FXMusicLibrary while one is live throws IllegalStateException, leaving the first
 * library's registry slot intact.
 *
 * The companion case confirms that an FX playlist populated with audio-item objects is immune
 * to any registry interference, since object-based population materialises items directly into
 * the observable cache without going through the shared registry.
 */
@DisplayName("MultiInstanceFXCorruptionTest")
@ExperimentalCoroutinesApi
internal class MultiInstanceFXCorruptionTest : StringSpec({

    val files = virtualFiles()

    lateinit var libraries: TwoFXMusicLibraries

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        FxToolkit.cleanupStages()
    }

    beforeEach {
        libraries = buildTwoFXLibrariesWithoutClosing(files.metadataIO)
    }

    afterEach {
        libraries.close()
    }

    "second FXMusicLibrary construction throws IllegalStateException while the first is live" {
        shouldThrow<IllegalStateException> {
            libraries.attemptSecondConstruction()
        }
    }

    "an FXMusicLibrary can be reconstructed after the previous one is closed" {
        // Release the fixture's live library so both slots are free before the cycle under test.
        libraries.close()

        LirpContext.default.registryFor(ObservableAudioItem::class.java) shouldBe null
        LirpContext.default.registryFor(ObservablePlaylist::class.java) shouldBe null

        // A fresh construction must not throw — the slots are available again.
        val second = FXMusicLibrary.builder().metadataIO(files.metadataIO).build()
        second.close()

        LirpContext.default.registryFor(ObservableAudioItem::class.java) shouldBe null
        LirpContext.default.registryFor(ObservablePlaylist::class.java) shouldBe null
    }

    "an FX playlist populated with audio-item objects resolves items correctly from its own library" {
        val itemInA = libraries.libraryA.audioItemFromFile(files.virtualAudioFile().next())

        // Object-based population materializes the item directly into the observable cache — no registry re-resolution.
        val playlist = libraries.libraryA.playlistHierarchy().createPlaylist("In-Memory Playlist")
        playlist.addAudioItem(itemInA)

        val resolvedItems =
            libraries.libraryA
                .playlistHierarchy()
                .findByName("In-Memory Playlist")
                .get()
                .audioItems
        resolvedItems.any { it.uniqueId == itemInA.uniqueId } shouldBe true
    }
})